package co.nayan.c3v2.core

import java.io.IOException

class ApiException(message: String) : IOException(message)
class UnAuthorizedException(message: String) : Exception(message)
class NotFoundException(message: String) : Exception(message)
class DuplicateException(message: String) : Exception(message)
class ServerStorageFullException(message: String) : Exception(message)
class AttendanceLockedException(message: String) : Exception(message)
class NoNetworkException(message: String) : IOException(message)
class ServerConnectionException(message: String) : Exception(message)