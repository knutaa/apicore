package no.paneon.api.utils;

import java.util.LinkedList;
import java.util.List;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ListExt<T> extends AbstractCollection<Element<T>> {

	static final Logger LOG = LogManager.getLogger(ListExt.class);

	private final Collection<T> c;

	public ListExt(Collection<T> c) {
		this.c = c;
	}

	@Override
	public Iterator<Element<T>> iterator() {
		final Iterator<T> iterator = c.iterator();
		return new Iterator<Element<T>>() {
			int index = 0;

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public Element<T> next() {
				T next = iterator.next();
				int current = index++;
				return new Element<>(current, current == 0, !iterator.hasNext(), next);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public int size() {
		return c.size();
	}
}

class Element<T> {
	public final int index;
	public final boolean first;
	public final boolean last;
	public final T value;

	public Element(int index, boolean first, boolean last, T value) {
		this.index = index;
		this.first = first;
		this.last = last;
		this.value = value;
	}
}

