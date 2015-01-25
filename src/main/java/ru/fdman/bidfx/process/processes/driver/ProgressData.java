package ru.fdman.bidfx.process.processes.driver;

/**
 * Created by fdman on 25.01.2015.
 */
public class ProgressData {
    private Double total;

    private String info;

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public ProgressData(Double total, String info) {
        this.total = total;
        this.info = info;
    }
}
