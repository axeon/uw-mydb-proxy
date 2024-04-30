package uw.mydb.parse;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uw.mydb.proxy.sqlparse.SqlParseResult;
import uw.mydb.proxy.sqlparse.SqlParser;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)//基准测试类型
@OutputTimeUnit(TimeUnit.MILLISECONDS)//基准测试结果的时间类型
@Warmup(iterations = 1, time = 5)//预热的迭代次数
@Threads(10)//测试线程数量
@State(Scope.Benchmark)//该状态为每个线程独享
//度量:iterations进行测试的轮次，time每轮进行的时长，timeUnit时长单位,batchSize批次数量
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS, batchSize = -1)
@SpringBootApplication
public class BenchmarkTest {

    private static String database = null;

    public static void main(String[] args) throws RunnerException {
//        SpringApplication.run(UwMydbApplication.class, args);
//        database = "";
        //初始化路由管理器
        Options opt = new OptionsBuilder().include( BenchmarkTest.class.getSimpleName() ).forks( 0 ).build();
        new Runner( opt ).run();
    }

    @Setup
    public void init() {
        uw.mydb.parse.SqlTest.init();
    }

    @Benchmark
    public void testInsert() {
        SqlParseResult result = new SqlParser( uw.mydb.parse.SqlTest.database, uw.mydb.parse.SqlTest.insert ).parse();

    }

    @Benchmark
    public void testUpdate() {
        SqlParseResult result = new SqlParser( uw.mydb.parse.SqlTest.database, uw.mydb.parse.SqlTest.update ).parse();

    }

    @Benchmark
    public void testSelect() {
        SqlParseResult result = new SqlParser( uw.mydb.parse.SqlTest.database, uw.mydb.parse.SqlTest.select ).parse();

    }


}
