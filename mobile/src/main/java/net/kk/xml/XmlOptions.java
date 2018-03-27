package net.kk.xml;

import net.kk.xml.adapter.XmlConstructorAdapter;
import net.kk.xml.adapter.XmlTextAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlOptions {
    //debug
    private boolean debug = false;
    //使用set方法
    private boolean useSetMethod = true;
    //xml缩进
    private boolean useSpace = false;
    //忽略静态
    private boolean ignoreStatic = true;
    private Map<Class<?>, XmlTextAdapter<?>> mXmlTypeAdapterMap;
    private Map<Class<?>, XmlConstructorAdapter> xmlConstructorAdapterMap;
    //忽略的类
    private List<Class<?>> mIgnoreClasses;
    /**
     * 忽略tag的大小写
     */
    private boolean ignoreTagCase = true;
    /***
     * 集合元素
     * <pre>
     *     list
     *     list
     *
     *     list
     *     list
     * </pre>
     * <pre>
     * lists
     *     list
     *     list
     * lists
     * </pre>
     */
    private boolean sameAsList = false;

    /***
     * 未标注的也算
     */
    private boolean useNoAnnotation = true;

    public boolean isIgnore(Class<?> cls) {
        if (mIgnoreClasses != null) {
            return mIgnoreClasses.contains(cls);
        }
        return false;
    }

    public Map<Class<?>, XmlTextAdapter<?>> getXmlTypeAdapterMap() {
        return mXmlTypeAdapterMap;
    }

    public Map<Class<?>, XmlConstructorAdapter> getXmlConstructorAdapterMap() {
        return xmlConstructorAdapterMap;
    }

    /***
     * @return use setXXXX
     */
    public boolean isUseSetMethod() {
        return useSetMethod;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isIgnoreTagCase() {
        return ignoreTagCase;
    }

    /***
     * @return
     */
    public boolean isUseSpace() {
        return useSpace;
    }

    public boolean isSameAsList() {
        return sameAsList;
    }

    /***
     * @return use no annotation
     */
    public boolean isUseNoAnnotation() {
        return useNoAnnotation;
    }

    private XmlOptions() {

    }

    public boolean isIgnoreStatic() {
        return ignoreStatic;
    }

    public static class Builder {
        private XmlOptions mOptions;

        public Builder() {
            mOptions = new XmlOptions();
        }

        public Builder(XmlOptions options) {
            this();
            if (options != null) {
                mOptions.useSetMethod = options.useSetMethod;
            }
        }

        /***
         * 忽略这些类，在write的时候忽略
         *
         * @param cls
         */
        public Builder ignore(Class<?> cls) {
            if (mOptions.mIgnoreClasses == null) {
                mOptions.mIgnoreClasses = new ArrayList<Class<?>>();
            }
            mOptions.mIgnoreClasses.add(cls);
            return this;
        }

        public XmlOptions build() {
            return mOptions;
        }

        public Builder dontIgnoreStatic() {
            mOptions.ignoreStatic = false;
            return this;
        }

        public Builder dontIgnoreTagCase() {
            mOptions.ignoreTagCase = false;
            return this;
        }

        public Builder dontUseSetMethod() {
            mOptions.useSetMethod = false;
            return this;
        }

        public Builder useSpace() {
            mOptions.useSpace = true;
            return this;
        }

        public Builder ignoreNoAnnotation() {
            mOptions.useNoAnnotation = false;
            return this;
        }

        public Builder enableSameAsList() {
            mOptions.sameAsList = true;
            return this;
        }

        public Builder debug() {
            mOptions.debug = true;
            return this;
        }

        public Builder registerConstructorAdapter(Class<?> cls, XmlConstructorAdapter xmlTypeAdapter) {
            if (mOptions.xmlConstructorAdapterMap == null) {
                mOptions.xmlConstructorAdapterMap = new HashMap<Class<?>, XmlConstructorAdapter>();
            }
            mOptions.xmlConstructorAdapterMap.put(ReflectUtils.wrapper(cls), xmlTypeAdapter);
            return this;
        }

        public Builder registerTypeAdapter(Class<?> cls, XmlTextAdapter<?> xmlTextAdapter) {
            if (mOptions.mXmlTypeAdapterMap == null) {
                mOptions.mXmlTypeAdapterMap = new HashMap<Class<?>, XmlTextAdapter<?>>();
            }
            mOptions.mXmlTypeAdapterMap.put(ReflectUtils.wrapper(cls), xmlTextAdapter);
            return this;
        }
    }
}
