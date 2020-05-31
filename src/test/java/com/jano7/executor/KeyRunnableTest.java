/*
MIT License

Copyright (c) 2019 Jan Gaspar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.jano7.executor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class KeyRunnableTest {

    private final Runnable r1 = new KeyRunnable<>("1", System::getenv);
    private final Runnable r2 = new KeyRunnable<>("1", System::currentTimeMillis);
    private final Runnable r3 = new KeyRunnable<>("2", System::currentTimeMillis);
    private final Runnable rNull = new KeyRunnable<>(null, System::currentTimeMillis);

    @Test
    public void keyRunnableHashCode() {
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r2.hashCode(), r3.hashCode());
        assertEquals(r1.hashCode(), "1".hashCode());
        assertEquals(rNull.hashCode(), 0);
    }

    @Test
    public void keyRunnableEquals() {
        assertEquals(r1, r2);
        assertNotEquals(r2, r3);
        assertNotEquals(rNull, r3);
        assertNotEquals(r3, rNull);
    }

    @Test
    public void keyRunnableToString() {
        assertEquals(r1.toString(), "1");
        assertEquals(rNull.toString(), "null");
    }
}
