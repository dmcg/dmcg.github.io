---
layout: post
title: Mocks v Approvals Tests Part 1
---

Aka: Test Driven Design v Test Driven Development

# Context

In my current gig at [Springer Nature](http://springernature.com) ([we're hiring :-](https://www.linkedin.com/jobs/springer-science-business-media-jobs)) I’ve been TDDing some Kotlin code to index data from a web service into Elastic Search. All the pieces are in place, they just need to be wired together and invoked.

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

The final step is to:

1. create the index
2. load the active ids
3. remove some that are excluded
4. pass the remainder to loadJournalWithArticles
5. then pass the returned Journals to the JournalIndexer.index().

There are complications of course, we should survive exceptions, and loadJournalWithArticles may return null (no such journal), but the indexer can’t index null. And it would be nice to keep track of how far we’ve got, and what worked and what didn’t. Let’s write a class called IndexRefresher.

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

Let’s suspend judgement of this code and test until we see what a mocking approach comes up with - it's [after the break](/2016/04/23/mocks-v-approvals-tests-part2/).
