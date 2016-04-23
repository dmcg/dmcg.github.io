---
layout: post
title: Mocks v Approvals Tests
---

Aka: Test Driven Design v Test Driven Development

# Context

In my current gig at [Springer Nature](http://springernature.com) (we're hiring :-) I’ve been TDDing some Kotlin code to index data from a web service into Elastic Search. All the pieces are in place, they just need to be wired together and invoked.

The components pieces are Journals

```
class Journals(...) {
   fun loadActiveIds(): List<String> {...}
   fun loadJournalWithArticles(id: String, articleCount: Int): Journal? {...}
}
```

and the JournalIndexer

```
class JournalIndexer(...) {
   fun createIndex() {...}
   fun index(journal: JournalJson) {...}
}
```

The final step is to create the index, load the active ids, remove some that are excluded, pass the remainder to loadJournalWithArticles, and then pass the returned Journals to the JournalIndexer.index(). There are complications of course, we should survive exceptions, and loadJournalWithArticles may return null (no such journal), but the indexer can’t index null. And it would be nice to keep track of how far we’ve got, and what worked and what didn’t. Let’s write a class called IndexRefresher.

# But First, A Test

Whether this is T.D.Development or T.D.Design, I should write a test first. We probably don’t want to hit the web service API or Elastic Search with a unit test, so my first instinct is to reach for JMock. But as it happens this little service hasn’t needed JMock yet, so let’s see how we get on without it.

# Approvals Testing

What we *do* have is an HttpFetcher abstraction that allows us to replay previous responses and an Approvals Testing library. We can use the former to use our actual implementation of Journals, and the latter to lock down the progress reporting and indexed data. The test takes quite a bit of setup, but the first time we run it we will hit the real data and can approve the results from processing it.

```
class IndexRefresherApprovalsTest {

   @Rule @JvmField val approver = ApprovalsRule.fileSystemRule("src/test/java")

   val transcript: Transcript by lazy { approver.transcript() }
   val cacheDir = File("src/test/resources/refresher-cache")
   val articles = Articles(ContentAPI(CachingHttpFetcher(File(cacheDir, "articles-http-cache"), httpFetcher)))
   val journals = Journals(ContentAPI(CachingHttpFetcher(File(cacheDir, "journals-http-cache"), httpFetcher)), articles)

   val refresher = object : ScratchIndexRefresher(5) {
       override fun println(message: String?) {
           transcript.appendLine(message)
       }
   }

   @Test fun indexes() {
       val indexer = object : ICanIndexJournals {
           override fun createIndex() {
               transcript.append("Index created")
           }
           override fun index(journal: JournalJson): Id<JournalJson> {
               transcript.appendFormatted(journal, jsonFormatter).endl()
               return Id(journal.id)
           }
       }

       val exclusions = readExcludedJournalIdsFrom(journals.javaClass, "/excluded-journals.csv")
       refresher.refresh(journals, indexer, exclusions)
   }
}
```

The implementation

```
open class ScratchIndexRefresher(private val articleCount: Int) {

   fun refresh(journals: Journals, indexer: ICanIndexJournals, excludedIds: Set<String>) {
       val idsToProcess = journals.loadActiveIds().filterNot { excludedIds.contains(it) }
       println("Starting - total journals = ${idsToProcess.size}")
       idsToProcess.forEach { journalId ->
           try {
               val journal = journals.loadJournalWithArticles(journalId, articleCount)
               if (journal != null) {
                   indexer.index(JournalJson(journal))
                   println("Indexed $journalId (${journal.title})")
               } else {
                   println("No data for journalId $journalId")
               }
           } catch(x: Exception) {
               kotlin.io.println("Exception processing journal ${journalId}, ${x.message}")
           }
       }
   }

   open fun println(message: String?) = kotlin.io.println(message)

}
```

The file we approve to pass the test next time looks like this

```
Starting - total journals = 2976
No data for journal Id 41406
{
 "id" : "299",
 "name" : "Plant Cell Reports",
 ...
}
Indexed 299 (Plant Cell Reports)
No data for journal Id 13646
{
 "id" : "12914",
 "name" : "BMC International Health and Human Rights",
...
```

Let’s suspend judgement of this code and test until we see what a mocking approach comes up with.

# Mocking

Pulling in JMock wasn’t too painful, but my pair and I did argue quite a bit more about the form of the test. This is what it ended up looking like, after maybe half a day, plus a little more playing with how to express expectations in Kotlin.

```
class IndexRefresherTest {

   @Rule @JvmField val mockery = JUnitRuleMockery()

   val progress = mockery.mock(IndexRefresher.Progress::class.java)
   val refresher = IndexRefresher(99, progress)

   val indexer = mockery.mock(ICanIndexJournals::class.java)
   val journals = mockery.mock(ITellYouAboutJournals::class.java)

   val journal1 = Journal("1", "title", null, null, emptyList(), 0.0, AccessType.OPEN_ACCESS, true, null)
   val journal2 = Journal("2", "title", null, null, emptyList(), 0.0, AccessType.OPEN_ACCESS, true, null)

   @Test fun indexes() {
       mockery.expecting {
           givenActiveJournalIds(
               "1" to returnValue(journal1),
               "2" to returnValue(journal2))

           oneOf(progress).reset(2)
           oneOf(indexer).createIndex()

           oneOf(indexer).index(JournalJson(journal1))
           oneOf(progress).indexed(journal1)

           oneOf(indexer).index(JournalJson(journal2))
           oneOf(progress).indexed(journal2)
       }
       refresher.refresh(journals, indexer, emptySet())
   }

   @Test fun skips_excluded_journals() {
       mockery.expecting {
           givenActiveJournalIds(
               "1" to returnValue(journal1),
               "2" to returnValue(journal2))

           oneOf(progress).reset(1)
           oneOf(indexer).createIndex()

           never(indexer).index(JournalJson(journal1))
           never(progress).indexed(journal1)

           oneOf(indexer).index(JournalJson(journal2))
           oneOf(progress).indexed(journal2)
       }
       refresher.refresh(journals, indexer, setOf("1"))
   }

   @Test fun skips_not_found_journals() {
       mockery.expecting {
           givenActiveJournalIds(
               "1" to returnValue(null),
               "2" to returnValue(journal2))

           oneOf(progress).reset(2)
           oneOf(indexer).createIndex()

           never(indexer).index(JournalJson(journal1))
           oneOf(progress).noJournal("1")

           oneOf(indexer).index(JournalJson(journal2))
           oneOf(progress).indexed(journal2)
       }
       refresher.refresh(journals, indexer, emptySet())
   }

   @Test fun reports_exceptions_and_continues() {
       mockery.expecting {
           val x = RuntimeException("oops")

           givenActiveJournalIds(
               "1" to throwException(x),
               "2" to returnValue(journal2))

           oneOf(progress).reset(2)
           oneOf(indexer).createIndex()

           never(indexer).index(JournalJson(journal1))
           oneOf(progress).exception("1", x)

           oneOf(indexer).index(JournalJson(journal2))
           oneOf(progress).indexed(journal2)
       }
       refresher.refresh(journals, indexer, emptySet())
   }

   private fun JUnitRuleMockery.expecting(block: MyExpectations.() -> Unit) {
       this.checking(MyExpectations().apply(block))
   }

   inner class MyExpectations : Expectations() {
       fun givenActiveJournalIds(vararg idResultPairs: Pair<String, Action>) {
           allowing(journals).loadActiveIds()
           will(returnValue(idResultPairs.map { it.first }))
           idResultPairs.forEach { pair ->
               allowing(journals).loadJournalWithArticles(pair.first, 99)
               will(pair.second)
           }
       }
   }
}
```

Here’s the implementation.

```
class IndexRefresher(private val articleCount: Int, private val progress: Progress) {

   interface Progress {
       fun reset(total: Int)
       fun indexed(journal: Journal)
       fun noJournal(id: String)
       fun exception(id: String, x: Exception)
   }

   fun refresh(journals: ITellYouAboutJournals, indexer: ICanIndexJournals, excludedIds: Set<String>) {
       val idsToProcess = journals.loadActiveIds().filterNot { excludedIds.contains(it) }
       progress.reset(idsToProcess.size)

       indexer.createIndex()

       idsToProcess.forEach { journalId ->
           try {
               val journal = journals.loadJournalWithArticles(journalId, articleCount)
               if (journal != null) {
                   indexer.index(JournalJson(journal))
                   progress.indexed(journal)
               } else {
                   progress.noJournal(journalId)
               }
           } catch(x: Exception) {
               progress.exception(journalId, x)
           }
       }
   }
}
```

# Comparing the Code

Whilst the algorithm is identical between both implementations, it's instructive to look at the detail differences.

Most obviously the mock-generated version has an abstraction for `Progress`, while in the approved version, the progress seam is introduced by overriding the `println` method. This forces the implementation to be an `open` class - in Java you wouldn't notice this, but in Kotlin classes are `final` by default (probably my biggest beef with the language). The `Progress` interface is undoubtedly nicer than the plain-old-println in the approved version, and a consequence of being made to think about the relationship between the refresher and reporting progress while we were writing the spec in the test. We could have introduced the interface in the approved version, but didn't have to in order to test it, so we came down on the side of pragmatism. This pragmatism also explains the nasty hiding of the `println` global function by a method - obviously we originally were originally just printing to the console, and the override gave us a dirty way to capture that output into the approved file.

More subtly, while both versions have an interface, `ICanIndexJournals`, to allow us to introduce selectable indexing behaviour, the mocked version introduces `ITellYouAboutJournals`, whereas the approved version is able to use the production `Journals` implementation. This introduction of interfaces just to facilitate testing is common, if controversial. Mockista's argue that it introduces seams that better express the structure of the problem, and can aid extensibility and reuse. Countering that is the introduction of complexity that is not actually solving todays' problem, and the difficulty finding a different name for the interface to a thing from its default implementation - should it be  `IIndexer` / `Indexer` or `Indexer` / `IndexerImpl`. Since discovering the `IAmAnInterfaceNameThatTellsYouWhatIDo` convention I'm more delighted by the opportunity for whimsy than bugged by the two names, but you may disagree.

By the way, if you're wondering why both versions pass the `journals` and `indexer` into the `refresh` method rather having them as fields, it's because in the actual code we want to rebuild them from scratch when we reindex (once a month or so) to avoid having any state lying around between runs.

# Comparing the Tests

If the implementation is largely the same, the tests differ wildly:

* The mock test runs very much faster
* The approvals test combines all the things that it is verifying into one test, the mock test shows what happens
* The details of the behaviour of the refresher are hidden inside in the approved file, and even then require interpretation. The mock makes the behaviour clear in the test.
* The mock test is able to show the behaviour of the code in the face of exceptions, the approvals test doesn’t. I suppose it could, but it would complicate things quite a bit.
* The approvals test exercises the Journals code against the real API, at least on its first run.
* The approvals test relies on HTTP caching magic, the mock test on JMock magic.
* The mock test took and implementation took half a day, perhaps longer than expected because I was working with a new pair. The approvals test and implementation took maybe an hour, but would have been longer had the HTTP caching already been in place.

You’ll have probably made up your mind which you prefer by now, but I’m still on the fence. The JMock test seems a lot more valuable in showing the behaviour of the code, but it doesn’t tell you anything you couldn’t get by looking at the actual implementation for 2 minutes. It will tell you if a change to that implementation changes its behaviour, but because it’s a strict unit test, won’t tell you if changes to any of its collaborators will change its behaviour. The approvals test is pretty useless at telling you what the code does, but that doesn’t matter if you can work it out in 2 minutes, and it will fail if changes to `Journals` or some of its dependencies change what it tries to index.

