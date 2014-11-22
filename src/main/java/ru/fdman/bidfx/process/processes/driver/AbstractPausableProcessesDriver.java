package ru.fdman.bidfx.process.processes.driver;

/**
 * Created by fdman on 06.07.2014.
 */
public abstract class AbstractPausableProcessesDriver implements IPausableProcessesDriver {
    protected ProcessDriverState processesDriverState = ProcessDriverState.STOPPED;

    @Override
    public abstract boolean isAnyProcessInProgress();
}
