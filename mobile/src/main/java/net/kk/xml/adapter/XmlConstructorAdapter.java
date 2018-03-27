package net.kk.xml.adapter;

public interface XmlConstructorAdapter {
    <T> T create(Class<T> tClass,Object parent);
}
