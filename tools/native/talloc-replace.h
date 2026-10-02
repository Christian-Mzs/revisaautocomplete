/* Minimal libc configuration for upstream talloc 2.1.14, built for Linux/Android. */
#ifndef REVISA_TALLOC_REPLACE_H
#define REVISA_TALLOC_REPLACE_H
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>
#include <sys/types.h>
#include <time.h>
#define HAVE_INTPTR_T 1
#define HAVE_VA_COPY 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define _PUBLIC_ __attribute__((visibility("default")))
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 1
#define TALLOC_BUILD_VERSION_RELEASE 14
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif
