package cn.com.bis.businesscardscanner;

/**
 * Created by qq on 2017/4/4.
 */
public class CommonUtils {
    public static boolean isNear(int a, int b) {
        boolean isNear = false;

        int minus = Math.abs(a - b);

        if(minus < 10) {
            isNear = true;
        }
        return isNear;
    }
}
