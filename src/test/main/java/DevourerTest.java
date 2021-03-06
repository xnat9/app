import cn.xnatural.app.Devourer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DevourerTest {
    static final Logger log = LoggerFactory.getLogger(DevourerTest.class);

    @Test
    void testParallel() throws Exception {
        Devourer devourer = new Devourer();
        AtomicInteger count = new AtomicInteger(0);
        devourer.parallel(2);
        long start = System.currentTimeMillis();
        AtomicBoolean stop = new AtomicBoolean(false);
        while (System.currentTimeMillis() - start < 1000 * 15 && !stop.get()) {
            devourer.offer(() -> {
                int p = devourer.parallel();
                if (p > 2) {
                    log.info("========================" + p);
                    stop.set(true);
                }
                log.info("wait " + devourer.getWaitingCount());
//                Utils.http().get("http://xnatural.cn:9090/test/cus?p1=" + count.getAndIncrement()).debug().execute();
//                try {
//                    Thread.sleep(2000 + new Random().nextInt(10000));
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            });
            // if (devourer.getWaitingCount() > 40) Thread.sleep(100 + new Random().nextInt(10000));
        }
        Thread.sleep(1000 * 60 * 30);
    }


    @Test
    void testSuspend() throws Exception {
        Devourer devourer = new Devourer();
        devourer.offer(() -> {
            log.info("执行");
            devourer.suspend(Duration.ofSeconds(10));
        });

        for (int i = 0; i < 100; i++) {
            int finalI = i;
            Thread.sleep(100);
            devourer.offer(() -> {
                log.info("执行 " + finalI);
            });
            log.info("added" + i);
        }
        Thread.sleep(10 * 1000);
        devourer.offer(() -> {
            log.info("执行 最后一个");
        });
        Thread.sleep(60 * 1000);
    }
}
