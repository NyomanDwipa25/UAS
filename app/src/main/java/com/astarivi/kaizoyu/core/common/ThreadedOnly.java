package com.astarivi.kaizoyu.core.common;

import java.lang.annotation.*;


@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ThreadedOnly {
}