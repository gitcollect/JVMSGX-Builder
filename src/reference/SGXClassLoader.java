package reference;

import java.net.URL;
import java.net.URLClassLoader;

public class SGXClassLoader extends URLClassLoader {
    public SGXClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }
}
