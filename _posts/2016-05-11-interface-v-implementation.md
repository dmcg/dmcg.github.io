---
layout: post
title: Interface v Implementation
tags: [Other]
---

# Context

Here at Springer Nature [(still hiring :-)](https://www.linkedin.com/jobs/springer-science-business-media-jobs) we have a single-page webapp that recommends academic journals via a small web service which in turn queries an Elastic Search index. The Elastic Search index needs to be refreshed periodically - we're not sure how often, probably once a month or so. The data that is indexed comes from an XML web service, merged and transmogrified in XPath'y ways.

If you've been reading along, yes, this is the same refresher I looked at in the first [2 episodes](/mocks-v-approvals-tests-part1.html).

# Now to Run It

We'd finished the code to update the index, and just needed a way to run it periodically. My colleague imported Quartz, and created a dummy Job implementation that printed "Hello World" on the 28th day of every month, courtesy of Quartz's `CronTrigger`.

Our `IndexUpdater` class has a constructor that takes the source and destination details, and it has a run() method that we'd like Quartz to call on the configured schedule - simples.

I started to tackle this task on the train on the way into work. The Internet connection was patchy, masking the fact that the Quartz website was down. But I had the source, and it seemed to be saying that I had to create a `JobDetail` object (via the inevitable `JobDetailBuilder`). The `JobDetail` references an implementation of a `Job` interface, that has an execute method. So all I have to do is make my `IndexUpdater` implement `Job`, and rename run to execute!

Except that isn't what Quartz does. It doesn't take an instance of a class that implements `Job`. It takes a reference to a class that implements `Job`, instantiates it using a no-arg constructor, then calls `newlyCreatedInstanceOfJob.execute()`. It requires reflection to do this, its one, erm, job.

How on earth did this get to be the case? How is the simple case of just calling a method on an object not the default? Somewhat tongue-in-cheek I tweeted:

["Is there any way to persuade Quartz to - how to describe this crazy use case - invoke a function on a schedule?"]( https://twitter.com/duncanmcg/status/712918584658419712)

and then asked the [same question on Stack Overflow](http://stackoverflow.com/q/36196191/97777).

Of course you can write code to thunk between the simple case and the reflecto-magic case ..., but surely it should be that the reflecto-magic is implemented in terms of the simple method call, not vice versa? Quartz developers, how did it get like that?

# What Quartz, Really?

Arriving at work, I was asked, "Why are you using Quartz if its interface is so bad?"

Now while there is a bit of me that distrusts the work of programmers who would have made the `JobDetail` their first choice, I do think that parsing Cron expresssions, running a job on schedule, making sure that a job can only be running one instance, recovering from errors etc, is a complex task. I'm pretty sure that if I tried it there would be at least one bug remaining after a day or two or work. I first used Quartz at least 10 years ago and there are thousands of projects depending on it - the chances are it's reliable and stable where it counts, even if enterprisey and naff in its interface.

Even after banging my head against that interface, we had scheduled tasks running with a configurable Cron pattern and invokable on demand via a POST in under 2 hours of work. Standing on the shoulders of giants should be our default choice, even if we sometimes have to wear orthotics to be comfortable there.