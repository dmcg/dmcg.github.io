# Bootstrap

You can build the site locally with jekyll. With rbenv installed, 
and using the Ruby specified
in .ruby-version, install jekyll
 
```
eval "$(rbenv init -)"

rbenv version

  2.2.3 (set by /Users/duncan/work/website-jekyll/site/.ruby-version)
  
gem install jekyll
```

# Build

Jekyll seems to load the gems specified in Gemfile, so 

```
jekyll serve
```

just seems to work 