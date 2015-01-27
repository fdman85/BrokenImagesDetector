package ru.fdman.bidfx.process.processes;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PoolUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.fdman.bidfx.process.processes.driver.ProgressData;
import ru.fdman.bidfx.process.processes.processor.FileBytesProcessor;
import ru.fdman.bidfx.process.processes.processor.algorithm.AlgorithmPoolFactory;
import ru.fdman.bidfx.process.processes.processor.algorithm.IAlgorithm;
import ru.fdman.bidfx.process.processes.processor.result.BytesProcessResult;

import java.io.File;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.*;

//import ru.fdman.bidfx.process.processes.processor.algorithm.AlgorithmPoolFactory;
//import ru.fdman.bidfx.process.processes.processor.algorithm.IAlgorithm;

/**
 * Created by fdman on 07.07.2014.
 */
public class BrokenImagesDetector extends PausableCallable {
    private static final long PROCESSING_WAIT_SECONDS = 10;
    private final Logger log = LoggerFactory
            .getLogger(BrokenImagesDetector.class);

    /**
     * Сервиc запуска процессоров алгоритмов
     */
    private final ExecutorService processorExecService;
    /**
     * Очередь, из которой брать данные для процессоров
     */
    private final BlockingQueue<Map<File, byte[]>> queue;
    /**
     * Пул из которого будут брать классы алгоритмов процессоры
     */
    private final ObjectPool<IAlgorithm> algorithmPool;

    /**
     * В этот список попадают все Future результаты
     */

    private final LinkedBlockingDeque<Future<BytesProcessResult>> futureAlgorithmResults;


    /**
     * @param detectorProcessesThreads - Количество потоков для обработки очереди
     * @param queue                    - Очередь, из которой брать данные для потоков обработки
     * @param futureAlgorithmResults   - Cписок куда попадают все Future результаты
     * @param algorithmClass           - Класс алгоритмов, которыми будут манпулировать потоки обработки и ими будут обрабатываться файлы
     */
    public BrokenImagesDetector(int detectorProcessesThreads,
                                BlockingQueue<Map<File, byte[]>> queue,
                                LinkedBlockingDeque<Future<BytesProcessResult>> futureAlgorithmResults,
                                Class<? extends IAlgorithm> algorithmClass) {
        this.processorExecService = Executors.newFixedThreadPool(detectorProcessesThreads,
                new BasicThreadFactory.Builder().namingPattern("BrokenImagesDetector - %d").build());
        this.queue = queue;
        this.futureAlgorithmResults = futureAlgorithmResults;
        //GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        this.algorithmPool = new GenericObjectPool<>(PoolUtils.synchronizedPooledFactory(new AlgorithmPoolFactory<>(algorithmClass))/*, poolConfig*/);
        //new GenericObjectPool<>(new AlgorithmPoolFactory<>(algorithmClass), poolConfig);
    }

    @Override
    public Object call() throws Exception {
        log.trace("BrokenImagesDetector STARTED");
        long start = Calendar.getInstance().getTimeInMillis();
        handleQueueAndPushFutureResultsToReportDeque();
        getNextFinishManager().setCanFinishFlag(true);
        Long delta = Calendar.getInstance().getTimeInMillis() - start;
        log.trace("BrokenImagesDetector FINISHED. Total time: {} ms", delta);
        return true;
    }

    private void handleQueueAndPushFutureResultsToReportDeque() {
        while (!Thread.interrupted()) {
            pauseIfNeeded();
            if (queue.peek() != null) {
                Map<File, byte[]> dataMap = queue.poll();
                //log.trace("File polled from queue");
                if (null != dataMap && !dataMap.isEmpty()) {
                    try {
                        //log.trace("BrokenImagesDetector before put last");
                        futureAlgorithmResults.putLast(processorExecService.submit(new FileBytesProcessor(dataMap, algorithmPool)));
                        //log.trace("BrokenImagesDetector after put last");
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            if (getFinishManager().getCanFinishFlag() && queue.peek() == null) {
                break;
            }
        }
        processorExecService.shutdown();
        try {
            processorExecService.awaitTermination(PROCESSING_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("processorExecService.awaitTermination {} {} {}", PROCESSING_WAIT_SECONDS, TimeUnit.SECONDS, ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public ProgressData getProgress() {
        return new ProgressData((new Integer(queue.size()).doubleValue()), "q "+queue.size()+" pool:"+this.algorithmPool.getNumActive()+"|"+this.algorithmPool.getNumIdle()); //TODO
    }
}
