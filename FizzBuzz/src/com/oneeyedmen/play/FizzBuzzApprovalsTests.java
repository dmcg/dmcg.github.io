package com.oneeyedmen.play;

import com.oneeyedmen.okeydoke.junit.ApprovalsRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public class FizzBuzzApprovalsTests {

    @Rule public ApprovalsRule approver = ApprovalsRule.fileSystemRule("src");

    @Test
    public void test() {
        approver.assertApproved(
            IntStream.range(1, 32).mapToObj((i) ->  i + "\t = \t" + fizzBuzz(i)).collect(joining("\n"))
        );
    }

    public String fizzBuzz(int i) {
        if (i % 15 == 0) return "FizzBuzz";
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }


}
