package ru.fdman.bidfx.process.processes.processor;

import org.apache.commons.pool2.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.fdman.bidfx.process.processes.processor.algorithm.IAlgorithm;
import ru.fdman.bidfx.process.processes.processor.result.BytesProcessResult;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: fdman
 * Date: 01.12.13
 */
public class FileBytesProcessor implements Callable<BytesProcessResult> {
    private static final AtomicLong counter = new AtomicLong(0);
    private final long id;
    private final Logger log = LoggerFactory
            .getLogger(FileBytesProcessor.class);

    private final byte[] bytes;
    private final File file;
    private final ObjectPool<IAlgorithm> algorithmPool;

    public FileBytesProcessor(Map<File, byte[]> dataMap, ObjectPool<IAlgorithm> algorithmPool) {
        id = counter.getAndIncrement();
        File file = null;
        for (File incomeFile : dataMap.keySet()) {
            file = incomeFile;
            break;
        }
        byte[] bytes = null;
        for (byte[] incomeBytes : dataMap.values()) {
            bytes = incomeBytes;
            break;
        }
        this.bytes = bytes;
        this.file = file;
        this.algorithmPool = algorithmPool;
    }


    @Override
    public BytesProcessResult call() {
        IAlgorithm algorithm = null;
        BytesProcessResult processResult = null;
        try {
            algorithm = algorithmPool.borrowObject();//
            algorithm.setData(bytes, file);
            processResult = algorithm.doWork();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (algorithm != null) {
                algorithm.clearData(); //clear algorithm data will be performed by a pool factory
                try {
                    algorithmPool.returnObject(algorithm);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Pool error. Object " + algorithm + " is not returned");
                }
            }
        }
        //log.trace("before return from FileBytesProcessor {}. result {}", id, processResult);
        return processResult;
    }
}
