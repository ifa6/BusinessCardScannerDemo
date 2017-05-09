package cn.com.bis.businesscardscanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by qq on 2017/4/4.
 */
public class OriginalData {
    public List<Integer> widths;
    public List<Integer> heights;
    private String word;
    private int idx;

    public OriginalData() {
        widths = new ArrayList<Integer>();
        heights = new ArrayList<Integer>();
        word = "";
        idx = 0;
    }

    public void addData(String word, int width, int height){
        this.word = this.word + word;
        this.widths.add(width);
        this.heights.add(height);
        idx++;
    }

    public String getWord() {
        return word;
    }

    public Integer getHeight() {
        int height = 0;
        int sum = 0;
        for(int t : heights) {
            sum = sum + t;
        }
        height = sum/heights.size();

        return height;
    }

    public Integer getWidth() {
        int width = 0;
        int sum = 0;
        for(int t : widths) {
            sum = sum + t;
        }
        width = sum/widths.size();

        return width;
    }

    public Integer getArea() {
        int area = 0;
        area = getWidth() * getHeight();
        return area;
    }
}
