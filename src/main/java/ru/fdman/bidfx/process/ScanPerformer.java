package ru.fdman.bidfx.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.fdman.bidfx.Constants;
import ru.fdman.bidfx.FileType;
import ru.fdman.bidfx.process.processes.*;
import ru.fdman.bidfx.process.processes.driver.PausableProcessesDriver;
import ru.fdman.bidfx.process.processes.processor.algorithm.IAlgorithm;
import ru.fdman.bidfx.process.processes.processor.result.BytesProcessResult;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Driver - mediator of a pausable processes
 * Created by fdman on 19.07.2014.
 */
public class ScanPerformer {
    private final PausableProcessesDriver pausableProcessesDriver;


    public ScanPerformer(String folderPath, Set<FileType> fileTypes, Class<? extends IAlgorithm> algorithmClass, Report report, Function<Void, Void> onFinish, Function<Void, Void> onCancel) {

        BlockingQueue<Map<File, byte[]>> filesQueue = new ArrayBlockingQueue<>(Constants.INPUT_QUEUE_SIZE_NUM);
        LinkedBlockingDeque<Future<BytesProcessResult>> algorithmResultsDeque = new LinkedBlockingDeque<>(Constants.INPUT_QUEUE_SIZE_NUM);
        List<PausableCallable<?>> pausableCallables = new LinkedList<>();
        PausableCallable<?> first = new FilesToQueueScanner(filesQueue, folderPath, fileTypes);
        PausableCallable<?> second = new BrokenImagesDetector(Constants.CPU_CORES_NUM, filesQueue, algorithmResultsDeque, algorithmClass);
        PausableCallable<?> third = new ResultsToReportConverter(algorithmResultsDeque, report);

        IFinishManager firstManager = new CommonFinishManager("FilesToQueueScanner");
        IFinishManager secondManager = new CommonFinishManager("BrokenImagesDetector");
        IFinishManager thirdManager = new CommonFinishManager("ResultsToReportConverter");

        first.setFinishManager(firstManager);
        first.setNextFinishManager(secondManager);
        second.setFinishManager(secondManager);
        second.setNextFinishManager(thirdManager);
        third.setFinishManager(thirdManager);
        third.setNextFinishManager(null);

        pausableCallables.add(first);
        pausableCallables.add(second);
        pausableCallables.add(third);

        pausableProcessesDriver = new PausableProcessesDriver(pausableCallables, onFinish, onCancel);
    }

    public synchronized void performScan() {
        pausableProcessesDriver.startProcesses();
    }

    public synchronized void cancelScan() {
        pausableProcessesDriver.cancelProcesses();
    }

    public synchronized void pauseScan() {
        pausableProcessesDriver.pauseProcesses();
    }

    public synchronized void unpauseScan() {
        pausableProcessesDriver.unpauseProcesses();
    }

    private class CommonFinishManager implements IFinishManager {
        private final Logger log = LoggerFactory
                .getLogger(CommonFinishManager.class);
        private final String name;

        private AtomicBoolean flag = new AtomicBoolean(false);

        private CommonFinishManager(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean getCanFinishFlag() {
            //log.debug("{} get canFinishFlag is {}", getName(), flag);
            return flag.get();
        }

        @Override
        public void setCanFinishFlag(boolean flag) {
            //log.debug("{} set canFinishFlag to {}", getName(), flag);
            this.flag.set(flag);
        }
    }

}
