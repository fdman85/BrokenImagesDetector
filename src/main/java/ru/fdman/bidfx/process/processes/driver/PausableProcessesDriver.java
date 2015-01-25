package ru.fdman.bidfx.process.processes.driver;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.fdman.bidfx.process.processes.PausableCallable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Created by fdman on 07.07.2014.
 */
public class PausableProcessesDriver implements IPausableProcessesDriver {

    protected ProcessDriverState processesDriverState = ProcessDriverState.STOPPED;
    private final List<PausableCallable<?>> callables;
    private final List<Future> callablesFutures;
    private final ExecutorService resultGetterExecutorService;
    private final Function<Void, Void> onFinish;
    private final Function<Void, Void> onCancel;
    private final Function<ProgressData, Void> refreshProgress;
    private final Logger log = LoggerFactory
            .getLogger(PausableProcessesDriver.class);
    private volatile boolean scanCancelled = false;

    public PausableProcessesDriver(List<PausableCallable<?>> pausableCallables, Function<Void, Void> onFinish, Function<Void, Void> onCancel, Function<ProgressData, Void> refreshProgress) {
        resultGetterExecutorService = Executors.newFixedThreadPool(pausableCallables.size(), new BasicThreadFactory.Builder().namingPattern("resultGetterExecutorService - %d").build());
        callablesFutures = new ArrayList<>(pausableCallables.size());
        this.callables=pausableCallables;
        this.onFinish = onFinish;
        this.onCancel = onCancel;
        this.refreshProgress = refreshProgress;

    }

    @Override
    public void startProcesses() {

        if (isAnyProcessInProgress()) {
            throw new IllegalStateException("Unable to start processes. Process already in progress");
        }

        synchronized (this) {
            if (processesDriverState == ProcessDriverState.STOPPED) {
                processesDriverState = ProcessDriverState.RUN;

                callables.forEach(pausableCallable -> {
                    Future objectFuture = resultGetterExecutorService.submit(pausableCallable);
                    callablesFutures.add(objectFuture);
                });


            } else {
                throw new IllegalStateException("Unable to start processes. Process is in state " + processesDriverState);
            }
        }

        new Thread(() -> {
            while (isAnyProcessInProgress()) {
                try {
                    StringBuilder sb = new StringBuilder();
                    for (PausableCallable<?> callable : this.callables) {
                        sb.append(callable.getName()).append(": ").append(callable.getProgress()).append(" ");
                    }

                    refreshProgress.apply(new ProgressData(new Random().nextDouble(), sb.toString()));
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    log.error(ExceptionUtils.getStackTrace(e));
                    processesDriverState = ProcessDriverState.STOPPED;
                    scanCancelled = true;
                    onCancel.apply(null);
                    return;
                }
            }
            if (!scanCancelled){
                onFinish.apply(null);
            }
            processesDriverState = ProcessDriverState.STOPPED;
            resultGetterExecutorService.shutdown();
        }, "PausableProcessesDriver - interrupt/finish watcher").start();
    }

    @Override
    public void pauseProcesses() {
        synchronized (this) {
            if (processesDriverState == ProcessDriverState.RUN) {
                callables.forEach(PausableCallable::pause);
                processesDriverState = ProcessDriverState.PAUSED;
            } else {
                throw new IllegalStateException("Process is in state " + processesDriverState);
            }
        }
    }

    /**
     * That method is also used in case when user cancel paused processes.
     * Therefore we must to unlock processes before really interrupting them
     *
     * @param waitForUnlock - generally use true when    TODO
     */
    //TODO param???
    public void unpauseProcesses(boolean waitForUnlock) {
        synchronized (this) {
            if (processesDriverState == ProcessDriverState.PAUSED) {
                /*if (waitForUnlock) {
                    callables.forEach(pausableCallable -> {
                        while (pausableCallable.isPaused()) {
                            pausableCallable.unpause();
                        }
                    });
                } else {
                    callables.forEach(pausableCallable -> pausableCallable.unpause());
                }        */
                callables.forEach(PausableCallable::unpause);
                processesDriverState = ProcessDriverState.RUN;
            } else {
                throw new IllegalStateException("Unable to unpause processes. Process is in state " + processesDriverState);
            }
        }
    }

    @Override
    public synchronized void unpauseProcesses() {
        unpauseProcesses(false);
    }

    @Override
    public void cancelProcesses() {
        synchronized (this) {
            if (processesDriverState == ProcessDriverState.PAUSED) { //resume before interrupt! And wait till processes became really unlocked
                unpauseProcesses(true);
            }
        }
        resultGetterExecutorService.shutdownNow();
        processesDriverState = ProcessDriverState.STOPPED;
        scanCancelled = true;
        onCancel.apply(null);
    }

    @Override
    public synchronized boolean isAnyProcessInProgress() {
        long activeProcesses = callablesFutures.stream().filter(objectFuture -> (!(objectFuture.isDone() || objectFuture.isCancelled()))).count();
        return activeProcesses > 0;
    }
}

enum ProcessDriverState {
    RUN,
    PAUSED,
    STOPPED
}