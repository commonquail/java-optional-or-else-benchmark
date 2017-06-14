# JMH benchmark: traditional null check vs. `Optional::orElse`

The JVM's *escape analysis* enables an optimization called *scalar
replacement*, which amounts to what people think of when they say "stack
allocation" in the context of Java. Escape analysis only runs at the max
optimization level, C2, which is reached after 10,000 method invocations by
default.

Creating an `Optional` allocates [[new]]. Can the JVM optimize away this
allocation?

## Conclusion

Yes.

Escape analysis does successfully yield the scalar replacement optimization for
`orElse` -- we never allocate:

| Benchmark                                      | Mode  | Cnt  |  Score |    Error  |  Units |
|:---------------------------------------------- |:----- | ----:| ------:| ---------:| ------:|
| OptionalScalar.null_check                      | avgt  |  15  |  2,975 | ±  0,050  |  ns/op |
| OptionalScalar.null_check:·asm                 | avgt  |      |    NaN |           |    --- |
| OptionalScalar.null_check:·gc.alloc.rate       | avgt  |  15  | ≈ 10⁻⁴ |           | MB/sec |
| OptionalScalar.null_check:·gc.alloc.rate.norm  | avgt  |  15  | ≈ 10⁻⁶ |           |   B/op |
| OptionalScalar.null_check:·gc.count            | avgt  |  15  |    ≈ 0 |           | counts |
| OptionalScalar.or_else                         | avgt  |  15  |  3,084 | ±  0,080  |  ns/op |
| OptionalScalar.or_else:·asm                    | avgt  |      |    NaN |           |    --- |
| OptionalScalar.or_else:·gc.alloc.rate          | avgt  |  15  | ≈ 10⁻⁴ |           | MB/sec |
| OptionalScalar.or_else:·gc.alloc.rate.norm     | avgt  |  15  | ≈ 10⁻⁶ |           |   B/op |
| OptionalScalar.or_else:·gc.count               | avgt  |  15  |    ≈ 0 |           | counts |

Curiously, `-prof perfasm` indicates `Optional`'s methods weren't inlined:

```asm
....[Hottest Region 1]..............................................................................
c2, level 4, foo.generated.OptionalScalar_or_else_jmhTest::or_else_avgt_jmhStub, version 599 (87 bytes)

...
  0,71%    0,96%  0x00007f6b724a6180: mov    0x50(%rsp),%r11
  0,39%    0,46%  0x00007f6b724a6185: mov    0xc(%r11),%r10d    ;*getfield o {reexecute=0 rethrow=0 return_oop=0}
                                                                ; - foo.OptionalScalar::or_else@1 (line 29)
                                                                ; - foo.generated.OptionalScalar_or_else_jmhTest::or_else_avgt_jmhStub@17 (line 232)
  7,83%    7,10%  0x00007f6b724a6189: test   %r10d,%r10d
                  0x00007f6b724a618c: jne    0x00007f6b724a6206  ;*ifnonnull {reexecute=0 rethrow=0 return_oop=0}
                                                                ; - java.util.Optional::ofNullable@1 (line 132)
                                                                ; - foo.OptionalScalar::or_else@4 (line 29)
                                                                ; - foo.generated.OptionalScalar_or_else_jmhTest::or_else_avgt_jmhStub@17 (line 232)
  1,77%    0,90%  0x00007f6b724a618e: movabs $0xd6a81270,%r10   ;   {oop(a &apos;java/util/Optional&apos;{0x00000000d6a81270})}
  0,56%    0,44%  0x00007f6b724a6198: mov    0xc(%r10),%r10d    ;*getfield value {reexecute=0 rethrow=0 return_oop=0}
                                                                ; - java.util.Optional::orElse@1 (line 347)
                                                                ; - foo.OptionalScalar::or_else@9 (line 29)
                                                                ; - foo.generated.OptionalScalar_or_else_jmhTest::or_else_avgt_jmhStub@17 (line 232)
  0,27%    0,25%  0x00007f6b724a619c: test   %r10d,%r10d
                  0x00007f6b724a619f: jne    0x00007f6b724a6225  ;*ifnull {reexecute=0 rethrow=0 return_oop=0}
                                                                ; - java.util.Optional::orElse@4 (line 347)
                                                                ; - foo.OptionalScalar::or_else@9 (line 29)
                                                                ; - foo.generated.OptionalScalar_or_else_jmhTest::or_else_avgt_jmhStub@17 (line 232)
```

We can prove that escape analysis caused the optimization by specifically
disabling it with the `-XX:-DoEscapeAnalysis` option:

| Benchmark                                                | Mode  | Cnt  |    Score |    Error  |  Units |
|:-------------------------------------------------------- | -----:| ----:| --------:| ---------:| ------:|
| OptionalScalar.null_check                                | avgt  |  15  |    3,014 | ±  0,071  |  ns/op |
| OptionalScalar.null_check:·asm                           | avgt  |      |      NaN |           |    --- |
| OptionalScalar.null_check:·gc.alloc.rate                 | avgt  |  15  |   ≈ 10⁻⁴ |           | MB/sec |
| OptionalScalar.null_check:·gc.alloc.rate.norm            | avgt  |  15  |   ≈ 10⁻⁶ |           |   B/op |
| OptionalScalar.null_check:·gc.count                      | avgt  |  15  |      ≈ 0 |           | counts |
| OptionalScalar.or_else                                   | avgt  |  15  |    5,793 | ±  0,054  |  ns/op |
| OptionalScalar.or_else:·asm                              | avgt  |      |      NaN |           |    --- |
| OptionalScalar.or_else:·gc.alloc.rate                    | avgt  |  15  | 1756,481 | ± 16,060  | MB/sec |
| OptionalScalar.or_else:·gc.alloc.rate.norm               | avgt  |  15  |   16,000 | ±  0,001  |   B/op |
| OptionalScalar.or_else:·gc.churn.PS_Eden_Space           | avgt  |  15  |    0,054 | ±  0,031  | MB/sec |
| OptionalScalar.or_else:·gc.churn.PS_Eden_Space.norm      | avgt  |  15  |   ≈ 10⁻³ |           |   B/op |
| OptionalScalar.or_else:·gc.churn.PS_Survivor_Space       | avgt  |  15  | 1758,137 | ± 63,048  | MB/sec |
| OptionalScalar.or_else:·gc.churn.PS_Survivor_Space.norm  | avgt  |  15  |   16,015 | ±  0,569  |   B/op |
| OptionalScalar.or_else:·gc.count                         | avgt  |  15  |  223,000 |           | counts |
| OptionalScalar.or_else:·gc.time                          | avgt  |  15  |  145,000 |           |     ms |

We can also prove that escape analysis only runs in C2 by disabling C2 with the
`-XX:TieredStopAtLevel=1` option [[c1]].

## Appendix: build and run

Install JDK 8 or up and Maven, then build and run with

```sh
$ mvn package
$ java -XX:+UseParallelGC -jar target/benchmarks.jar -prof gc
```

For `-prof perfasm` [[asm]] you first need to build the hsdis library [[hsdis]]
and instead run `java` with `LD_LIBRARY_PATH=<path to hsdis*.so>`.

[asm]: https://wiki.openjdk.java.net/display/HotSpot/PrintAssembly
[c1]: https://stackoverflow.com/a/38721975/482758
[hsdis]: https://gitlab.com/mkjeldsen/build-hsdis-binutils
[new]: http://hg.openjdk.java.net/jdk8/jdk8/jdk/file/jdk8-b132/src/share/classes/java/util/Optional.java#l108
