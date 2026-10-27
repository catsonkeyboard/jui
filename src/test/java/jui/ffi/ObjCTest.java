package jui.ffi;

import jui.testing.Check;

public final class ObjCTest {
    public void testAnArrayTakesAnyNumberOfObjects() { // M2 final review minor: a fixed 8-slot buffer
        long pool = ObjC.poolPush();
        try {
            long[] objects = new long[20];
            for (int i = 0; i < objects.length; i++) {
                objects[i] = ObjC.nsString("item " + i);
            }
            long array = ObjC.array(objects);
            Check.equal(20L, ObjC.send(array, "count"));
            for (int i = 0; i < objects.length; i++) {
                Check.equal("item " + i, ObjC.string(ObjC.send(array, "objectAtIndex:", i)));
            }
            Check.equal(2L, ObjC.send(ObjC.array(objects[0], objects[1]), "count"));   // small ones still work
            Check.equal(0L, ObjC.send(ObjC.array(), "count"));
        } finally {
            ObjC.poolPop(pool);
        }
    }
}
