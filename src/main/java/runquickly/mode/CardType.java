package runquickly.mode;

/**
 * Created by pengyi
 * Date : 16-6-12.
 */
public enum CardType {

    DANPAI("单牌", 1),
    DUIPAI("对牌", 2),
    LIANDUI("连对", 3),
    SHUNZI("顺子", 4),
    SANZHANG("三张", 5),
    FEIJI("飞机", 6),
    ZHADAN("炸弹", 7),
    SIDAIER("四带2", 8),
    ERROR("错误", 9);

    private String name;
    private Integer values;

    CardType(String name, Integer values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getValues() {
        return values;
    }

    public void setValues(Integer values) {
        this.values = values;
    }
}
