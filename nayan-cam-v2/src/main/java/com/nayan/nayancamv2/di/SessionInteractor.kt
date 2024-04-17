package com.nayan.nayancamv2.di

import co.nayan.appsession.SessionRepositoryInterface
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor

/**
 * Interactor to log all sessions
 *
 */
interface SessionInteractor : NayanCamModuleInteractor {
    fun getSessionRepositoryInterface(): SessionRepositoryInterface
}