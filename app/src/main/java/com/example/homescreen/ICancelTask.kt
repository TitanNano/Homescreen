package com.example.homescreen

import android.os.AsyncTask

interface ICancelTask {
    fun getStatus(): AsyncTask.Status
    fun cancel(mayInterruptIfRunning: Boolean): Boolean
}