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
    SANLIAN("三连", 6),
    FEIJI("飞机", 7),
    ZHADAN("炸弹", 8),
    SIZHANG("四张", 9),
    ERROR("错误", 10);

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
