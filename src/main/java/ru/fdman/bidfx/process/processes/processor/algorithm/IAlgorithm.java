package ru.fdman.bidfx.process.processes.processor.algorithm;

import ru.fdman.bidfx.process.processes.processor.result.BytesProcessResult;

import java.io.File;

/**
 * Created by fdman on 13.07.2014.
 */
public interface IAlgorithm {

    BytesProcessResult doWork();

    void setData(byte[] data, File file);

    void clearData();
}
