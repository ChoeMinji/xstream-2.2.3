/*
 * Copyright (C) 2005 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008, 2010, 2011, 2013, 2016, 2020 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 12. April 2005 by Joe Walnes
 */
package com.thoughtworks.xstream.converters.javabean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.ErrorWritingException;
import com.thoughtworks.xstream.converters.reflection.ObjectAccessException;


public class BeanProvider implements JavaBeanProvider {

    /**
     * @deprecated As of 1.4.6
     */
    protected static final Object[] NO_PARAMS = new Object[0];
    protected PropertyDictionary propertyDictionary;

    /**
     * Construct a BeanProvider that will process the bean properties in their natural order.
     */
    public BeanProvider() {
        this(new PropertyDictionary(new NativePropertySorter()));
    }

    /**
     * Construct a BeanProvider with a comparator to sort the bean properties by name in the dictionary.
     * 
     * @param propertyNameComparator the comparator
     */
    public BeanProvider(final Comparator propertyNameComparator) {
        this(new PropertyDictionary(new ComparingPropertySorter(propertyNameComparator)));
    }

    /**
     * Construct a BeanProvider with a provided property dictionary.
     * 
     * @param propertyDictionary the property dictionary to use
     * @since 1.4
     */
    public BeanProvider(final PropertyDictionary propertyDictionary) {
        this.propertyDictionary = propertyDictionary;
    }

    public Object newInstance(Class type) {
        ErrorWritingException ex = null;
        if (type == void.class || type == Void.class) {
            ex = new ConversionException("Security alert: Marshalling rejected");
        } else {
            try {
                return type.newInstance();
            } catch (InstantiationException e) {
                ex = new ConversionException("Cannot construct type", e);
            } catch (IllegalAccessException e) {
                ex = new ObjectAccessException("Cannot construct type", e);
            } catch (SecurityException e) {
                ex = new ObjectAccessException("Cannot construct type", e);
            } catch (ExceptionInInitializerError e) {
                ex = new ConversionException("Cannot construct type", e);
            }
        }
        ex.add("construction-type", type.getName());
        throw ex;
    }

    public void visitSerializableProperties(Object object, JavaBeanProvider.Visitor visitor) {
        PropertyDescriptor[] propertyDescriptors = getSerializableProperties(object);
        for (int i = 0; i < propertyDescriptors.length; i++) {
            ErrorWritingException ex = null;
            PropertyDescriptor property = propertyDescriptors[i];
            try {
                Method readMethod = property.getReadMethod();
                String name = property.getName();
                Class definedIn = readMethod.getDeclaringClass();
                if (visitor.shouldVisit(name, definedIn)) {
                    Object value = readMethod.invoke(object, new Object[0]);
                    visitor.visit(name, property.getPropertyType(), definedIn, value);
                }
            } catch (IllegalArgumentException e) {
                ex = new ConversionException("Cannot get property", e);
            } catch (IllegalAccessException e) {
                ex = new ObjectAccessException("Cannot access property", e);
            } catch (InvocationTargetException e) {
                ex = new ConversionException("Cannot get property", e.getTargetException());
            }
            if (ex != null) {
                ex.add("property", object.getClass() + "." + property.getName());
                throw ex;
            }
        }
    }

    public void writeProperty(Object object, String propertyName, Object value) {
        ErrorWritingException ex = null;
        PropertyDescriptor property = getProperty(propertyName, object.getClass());
        try {
            property.getWriteMethod().invoke(object, new Object[]{value});
        } catch (IllegalArgumentException e) {
            ex = new ConversionException("Cannot set property", e);
        } catch (IllegalAccessException e) {
            ex = new ObjectAccessException("Cannot access property", e);
        } catch (InvocationTargetException e) {
            ex = new ConversionException("Cannot set property", e.getTargetException());
        }
        if (ex != null) {
            ex.add("property", object.getClass() + "." + property.getName());
            throw ex;
        }
    }

    public Class getPropertyType(Object object, String name) {
        return getProperty(name, object.getClass()).getPropertyType();
    }

    public boolean propertyDefinedInClass(String name, Class type) {
        return propertyDictionary.propertyDescriptorOrNull(type, name) != null;
    }

    /**
     * Returns true if the Bean provider can instantiate the specified class
     */
    public boolean canInstantiate(Class type) {
        try {
            return type != null && newInstance(type) != null;
        } catch (final ErrorWritingException e) {
            return false;
        }
    }

    /**
     * Returns the default constructor, or null if none is found
     * 
     * @param type
     * @deprecated As of 1.4.6 use {@link #newInstance(Class)} or {@link #canInstantiate(Class)} directly.
     */
    protected Constructor getDefaultConstrutor(Class type) {

        Constructor[] constructors = type.getConstructors();
        for (int i = 0; i < constructors.length; i++) {
            Constructor c = constructors[i];
            if (c.getParameterTypes().length == 0 && Modifier.isPublic(c.getModifiers()))
                return c;
        }
        return null;
    }

    protected PropertyDescriptor[] getSerializableProperties(Object object) {
        List result = new ArrayList();
        for (final Iterator iter = propertyDictionary.propertiesFor(object.getClass()); iter.hasNext();) {
            final PropertyDescriptor descriptor = (PropertyDescriptor)iter.next();
            if (canStreamProperty(descriptor)) {
                result.add(descriptor);
            }
        }
        return (PropertyDescriptor[])result.toArray(new PropertyDescriptor[result.size()]);
    }

    protected boolean canStreamProperty(PropertyDescriptor descriptor) {
        return descriptor.getReadMethod() != null && descriptor.getWriteMethod() != null;
    }

    public boolean propertyWriteable(String name, Class type) {
        PropertyDescriptor property = getProperty(name, type);
        return property.getWriteMethod() != null;
    }

    protected PropertyDescriptor getProperty(String name, Class type) {
        return (PropertyDescriptor)propertyDictionary.propertyDescriptor(type, name);
    }

    /**
     * @deprecated As of 1.4 use {@link JavaBeanProvider.Visitor}
     */
    public interface Visitor extends JavaBeanProvider.Visitor {}
}
