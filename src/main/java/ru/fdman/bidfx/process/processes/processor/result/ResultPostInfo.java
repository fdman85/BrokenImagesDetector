package ru.fdman.bidfx.process.processes.processor.result;

import ru.fdman.bidfx.Status;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fdman on 04.02.2015.
 */
public class ResultPostInfo {
    private Map<Status, Long> byStatusesMap = new HashMap<>();

    public ResultPostInfo() {
        for (Status status : Status.values()) {
            byStatusesMap.put(status, 0L);
        }
    }

    private Status worstStatus = Status.FOLDER;

    private long totalInside = 0;

    public Map<Status, Long> getByStatusesMap() {
        return byStatusesMap;
    }

    public void setByStatusesMap(Map<Status, Long> byStatusesMap) {
        this.byStatusesMap = byStatusesMap;
    }

    public Status getWorstStatus() {
        return worstStatus;
    }

    public void setWorstStatus(Status worstStatus) {
        this.worstStatus = worstStatus;
    }

    public long getTotalInside() {
        return totalInside;
    }

    public void setTotalInside(long totalInside) {
        this.totalInside = totalInside;
    }
}
