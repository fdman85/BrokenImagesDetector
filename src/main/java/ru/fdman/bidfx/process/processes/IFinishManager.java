package ru.fdman.bidfx.process.processes;

public interface IFinishManager {

    boolean getCanFinishFlag();
    void setCanFinishFlag(boolean atomicBoolean);
}
