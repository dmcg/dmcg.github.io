#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${DIR}

if bundle exec jekyll serve ; then
    echo "Excellent"
else
    echo "Have you run"
    echo 'eval "$(rbenv init -)"'
fi

