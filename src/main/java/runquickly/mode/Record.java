package runquickly.mode;

import java.util.ArrayList;
import java.util.List;

public class Record {

    private List<OperationHistory> historyList = new ArrayList<>();
    private List<SeatRecord> seatRecordList = new ArrayList<>();//座位战绩信息

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
