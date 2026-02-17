package ca.ualberta.cs.cmput402.ghdow;

import java.io.IOException;

@FunctionalInterface
interface IOSupplier<T> {
    T get() throws IOException;
}

