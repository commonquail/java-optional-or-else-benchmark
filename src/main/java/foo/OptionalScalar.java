package foo;

import java.util.*;
import java.util.concurrent.*;

import org.openjdk.jmh.annotations.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class OptionalScalar {
    Object o;

    @Setup
    public void shake() {
        o = ThreadLocalRandom.current().nextBoolean() ? "foo" : null;
    }

    @Benchmark
    public Object null_check() {
        return o != null ? o : "bar";
    }

    @Benchmark
    public Object or_else() {
        return Optional.ofNullable(o).orElse("bar");
    }
}
