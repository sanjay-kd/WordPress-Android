package org.wordpress.android.viewmodel

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer

/**
 * A lifecycle-aware observable that sends only new updates after subscription, used for events like
 * navigation and SnackBar messages.
 *
 *
 * This avoids a common problem with events: on configuration change (like rotation) an update
 * can be emitted if the observer is active.
 *
 *
 * Note that only one observer can be subscribed.
 */
class SingleEventObservable<T>(private val sourceLiveData: LiveData<T>) {
    private var lastEvent: T? = null

    fun observe(owner: LifecycleOwner, observer: Observer<T>) {
        if (sourceLiveData.hasObservers()) {
            throw IllegalStateException("SingleEventObservable can be observed only by a single observer.")
        }
        sourceLiveData.observe(owner, Observer {
            if (it !== lastEvent) {
                lastEvent = it
                observer.onChanged(it)
            }
        })
    }
}
