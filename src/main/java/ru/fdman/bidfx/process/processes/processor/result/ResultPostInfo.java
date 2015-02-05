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

    public void addValueToByStatusesMap(Status status, long value){
        addToByStatusesMap(byStatusesMap, status, value);
        //byStatusesMap.put(status, byStatusesMap.get(status)+value);
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

    public static void addStatusesMapToFirst(Map<Status, Long> aMap1, Map<Status, Long> aMap2) {
        for (Status status : aMap2.keySet()) {
            addToByStatusesMap(aMap1, status, aMap2.get(status));
        }

    }

    private static void addToByStatusesMap(Map<Status, Long> aMap, Status status, long value){
        aMap.put(status, aMap.get(status)+value);
    }
}
