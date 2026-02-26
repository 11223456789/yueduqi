package com.peiyu.reader.exception

import com.peiyu.reader.R
import splitties.init.appCtx

class NoBooksDirException: NoStackTraceException(appCtx.getString(R.string.no_books_dir))
