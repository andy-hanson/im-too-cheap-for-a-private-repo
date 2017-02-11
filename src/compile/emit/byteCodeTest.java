package compile.emit;

import java.util.*;

public class byteCodeTest {
    public static void a() {
        a();
    }


    /*
    public static void f(int i) {
        String[] a = {};
        Arrays.sort(a, (x, y) -> x.compareTo(y) + i);
    }

    public static void g() {
        List<String> a = new ArrayList<String>();
        a.forEach(""::compareTo);
    }*/

    // This kind isn't too important for now.
    //public static void g() {
    //   String[] a = {};
    //    java.util.Arrays.sort(a, String::compareTo);
    //}
}
