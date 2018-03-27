package net.kk.xml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface XmlElement {
    String ITEM = "item";
    String KEY = "key";
    String VALUE = "value";

    /**
     * 名字
     */
    String value();

    /**
     * 同名
     */
    String alias() default "";

    String listItem() default ITEM;

    String namespace() default "";
}
