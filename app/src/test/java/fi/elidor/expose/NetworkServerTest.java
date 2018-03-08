package fi.elidor.expose;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * Created by teppo on 7.3.2018.
 */

public class NetworkServerTest {
    @Test
    public void integer_to_json() throws Exception {
        String json = NetworkServer.toJson(4);
        assertEquals(json, "4");
    }

    @Test
    public void string_to_json() throws Exception {
        String json = NetworkServer.toJson("hello");
        assertEquals(json, "\"hello\"");
    }

    @Test
    public void float_to_json() throws Exception {
        String json = NetworkServer.toJson(1.2345f);
        assertEquals(json, "1.2345");
    }

    @Test
    public void double_to_json() throws Exception {
        String json = NetworkServer.toJson(1.2345);
        assertEquals(json, "1.2345");
    }

    @Test
    public void object_to_json() throws Exception {
        HashMap sub = new HashMap();
        sub.put("c", 2);
        HashMap map = new HashMap();
        map.put("a", 1);
        map.put("b", sub);
        String json = NetworkServer.toJson(map);
        assertEquals(json, "{\"a\":1,\"b\":{\"c\":2}}");
    }

    @Test
    public void array_to_json() throws Exception {
        int[] sub = new int[] {1,2};
        Object[] arr = new Object[] {
                "a", 3, 3.4, sub
        };
        String json = NetworkServer.toJson(arr);
        System.out.println(json);
        assertEquals(json, "[\"a\",3,3.4,[1,2]]");
    }
}
