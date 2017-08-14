package runquickly.mode;

import java.util.List;

public class Record {

    private int multiple;
    private List<OperationHistory> historyList;
    private List<SeatRecord> seatRecordList;//座位战绩信息

    public int getMultiple() {
        return multiple;
    }

    public void setMultiple(int multiple) {
        this.multiple = multiple;
    }

    public List<OperationHistory> getHistoryList() {
        return historyList;
    }

    public void setHistoryList(List<OperationHistory> historyList) {
        this.historyList = historyList;
    }

    public List<SeatRecord> getSeatRecordList() {
        return seatRecordList;
    }

    public void setSeatRecordList(List<SeatRecord> seatRecordList) {
        this.seatRecordList = seatRecordList;
    }
}
