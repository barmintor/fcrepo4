/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.api.functions;

import static java.util.Objects.requireNonNull;

/**
 *
 * @author barmintor
 *
 * @param <A>
 * @param <B>
 */
public interface InjectiveConverter<A,B> extends InjectiveFunction<A, B>, Converter<A, B> {
    public static final Identity<?> IDENTITY = new Identity<>();

    @Override
    public default InjectiveConverter<B, A> inverse() {
        return new InverseConverterWrapper<>(this);
    }

    /**
     * A typed composition
     * @see java.util.function.Function
     * @param after
     * @return
     */
    public default <C> InjectiveConverter<A, C> andThen(final InjectiveConverter<B, C> after) {
        return new CompositeInjectiveConverter<>(this, requireNonNull(after, "Cannot compose with null!"));
    }

    /**
     * A typed composition
     * @see java.util.function.Function
     * @param before
     * @return
     */
    public default <C> InjectiveConverter<C, B> compose(final InjectiveConverter<C, A> before) {
        return new CompositeInjectiveConverter<>(requireNonNull(before, "Cannot compose with null!"), this);
    }

    /**
     * Convenience method for building a typed identity function
     * @param klass
     * @return
     */
    @SuppressWarnings("unchecked")
    public default <T> InjectiveConverter<T, T> identity(final Class<T> klass) {
        return (InjectiveConverter<T, T>) IDENTITY;
    }

    static class Identity<T> implements InjectiveConverter<T, T> {
        @Override
        public T toDomain(final T rangeValue) {
            return rangeValue;
        }

        @Override
        public T apply(final T t) {
            return t;
        }

        @Override
        public boolean inDomain(final T a) {
            return a != null;
        }

        @Override
        public InjectiveConverter<T, T> inverse() {
            return this;
        }

        @Override
        public <C> Converter<T, C> andThen(final Converter<T, C> after) {
            return after;
        }

        @Override
        public <C> Converter<C, T> compose(final Converter<C, T> before) {
            return before;
        }
    }
}
