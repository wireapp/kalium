// Generated by Apple Swift version 5.3.2 (swiftlang-1200.0.45 clang-1200.0.32.28)
#ifndef WIRESYSTEM_SWIFT_H
#define WIRESYSTEM_SWIFT_H
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wgcc-compat"

#if !defined(__has_include)
# define __has_include(x) 0
#endif
#if !defined(__has_attribute)
# define __has_attribute(x) 0
#endif
#if !defined(__has_feature)
# define __has_feature(x) 0
#endif
#if !defined(__has_warning)
# define __has_warning(x) 0
#endif

#if __has_include(<swift/objc-prologue.h>)
# include <swift/objc-prologue.h>
#endif

#pragma clang diagnostic ignored "-Wauto-import"
#include <Foundation/Foundation.h>
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#if !defined(SWIFT_TYPEDEFS)
# define SWIFT_TYPEDEFS 1
# if __has_include(<uchar.h>)
#  include <uchar.h>
# elif !defined(__cplusplus)
typedef uint_least16_t char16_t;
typedef uint_least32_t char32_t;
# endif
typedef float swift_float2  __attribute__((__ext_vector_type__(2)));
typedef float swift_float3  __attribute__((__ext_vector_type__(3)));
typedef float swift_float4  __attribute__((__ext_vector_type__(4)));
typedef double swift_double2  __attribute__((__ext_vector_type__(2)));
typedef double swift_double3  __attribute__((__ext_vector_type__(3)));
typedef double swift_double4  __attribute__((__ext_vector_type__(4)));
typedef int swift_int2  __attribute__((__ext_vector_type__(2)));
typedef int swift_int3  __attribute__((__ext_vector_type__(3)));
typedef int swift_int4  __attribute__((__ext_vector_type__(4)));
typedef unsigned int swift_uint2  __attribute__((__ext_vector_type__(2)));
typedef unsigned int swift_uint3  __attribute__((__ext_vector_type__(3)));
typedef unsigned int swift_uint4  __attribute__((__ext_vector_type__(4)));
#endif

#if !defined(SWIFT_PASTE)
# define SWIFT_PASTE_HELPER(x, y) x##y
# define SWIFT_PASTE(x, y) SWIFT_PASTE_HELPER(x, y)
#endif
#if !defined(SWIFT_METATYPE)
# define SWIFT_METATYPE(X) Class
#endif
#if !defined(SWIFT_CLASS_PROPERTY)
# if __has_feature(objc_class_property)
#  define SWIFT_CLASS_PROPERTY(...) __VA_ARGS__
# else
#  define SWIFT_CLASS_PROPERTY(...)
# endif
#endif

#if __has_attribute(objc_runtime_name)
# define SWIFT_RUNTIME_NAME(X) __attribute__((objc_runtime_name(X)))
#else
# define SWIFT_RUNTIME_NAME(X)
#endif
#if __has_attribute(swift_name)
# define SWIFT_COMPILE_NAME(X) __attribute__((swift_name(X)))
#else
# define SWIFT_COMPILE_NAME(X)
#endif
#if __has_attribute(objc_method_family)
# define SWIFT_METHOD_FAMILY(X) __attribute__((objc_method_family(X)))
#else
# define SWIFT_METHOD_FAMILY(X)
#endif
#if __has_attribute(noescape)
# define SWIFT_NOESCAPE __attribute__((noescape))
#else
# define SWIFT_NOESCAPE
#endif
#if __has_attribute(ns_consumed)
# define SWIFT_RELEASES_ARGUMENT __attribute__((ns_consumed))
#else
# define SWIFT_RELEASES_ARGUMENT
#endif
#if __has_attribute(warn_unused_result)
# define SWIFT_WARN_UNUSED_RESULT __attribute__((warn_unused_result))
#else
# define SWIFT_WARN_UNUSED_RESULT
#endif
#if __has_attribute(noreturn)
# define SWIFT_NORETURN __attribute__((noreturn))
#else
# define SWIFT_NORETURN
#endif
#if !defined(SWIFT_CLASS_EXTRA)
# define SWIFT_CLASS_EXTRA
#endif
#if !defined(SWIFT_PROTOCOL_EXTRA)
# define SWIFT_PROTOCOL_EXTRA
#endif
#if !defined(SWIFT_ENUM_EXTRA)
# define SWIFT_ENUM_EXTRA
#endif
#if !defined(SWIFT_CLASS)
# if __has_attribute(objc_subclassing_restricted)
#  define SWIFT_CLASS(SWIFT_NAME) SWIFT_RUNTIME_NAME(SWIFT_NAME) __attribute__((objc_subclassing_restricted)) SWIFT_CLASS_EXTRA
#  define SWIFT_CLASS_NAMED(SWIFT_NAME) __attribute__((objc_subclassing_restricted)) SWIFT_COMPILE_NAME(SWIFT_NAME) SWIFT_CLASS_EXTRA
# else
#  define SWIFT_CLASS(SWIFT_NAME) SWIFT_RUNTIME_NAME(SWIFT_NAME) SWIFT_CLASS_EXTRA
#  define SWIFT_CLASS_NAMED(SWIFT_NAME) SWIFT_COMPILE_NAME(SWIFT_NAME) SWIFT_CLASS_EXTRA
# endif
#endif
#if !defined(SWIFT_RESILIENT_CLASS)
# if __has_attribute(objc_class_stub)
#  define SWIFT_RESILIENT_CLASS(SWIFT_NAME) SWIFT_CLASS(SWIFT_NAME) __attribute__((objc_class_stub))
#  define SWIFT_RESILIENT_CLASS_NAMED(SWIFT_NAME) __attribute__((objc_class_stub)) SWIFT_CLASS_NAMED(SWIFT_NAME)
# else
#  define SWIFT_RESILIENT_CLASS(SWIFT_NAME) SWIFT_CLASS(SWIFT_NAME)
#  define SWIFT_RESILIENT_CLASS_NAMED(SWIFT_NAME) SWIFT_CLASS_NAMED(SWIFT_NAME)
# endif
#endif

#if !defined(SWIFT_PROTOCOL)
# define SWIFT_PROTOCOL(SWIFT_NAME) SWIFT_RUNTIME_NAME(SWIFT_NAME) SWIFT_PROTOCOL_EXTRA
# define SWIFT_PROTOCOL_NAMED(SWIFT_NAME) SWIFT_COMPILE_NAME(SWIFT_NAME) SWIFT_PROTOCOL_EXTRA
#endif

#if !defined(SWIFT_EXTENSION)
# define SWIFT_EXTENSION(M) SWIFT_PASTE(M##_Swift_, __LINE__)
#endif

#if !defined(OBJC_DESIGNATED_INITIALIZER)
# if __has_attribute(objc_designated_initializer)
#  define OBJC_DESIGNATED_INITIALIZER __attribute__((objc_designated_initializer))
# else
#  define OBJC_DESIGNATED_INITIALIZER
# endif
#endif
#if !defined(SWIFT_ENUM_ATTR)
# if defined(__has_attribute) && __has_attribute(enum_extensibility)
#  define SWIFT_ENUM_ATTR(_extensibility) __attribute__((enum_extensibility(_extensibility)))
# else
#  define SWIFT_ENUM_ATTR(_extensibility)
# endif
#endif
#if !defined(SWIFT_ENUM)
# define SWIFT_ENUM(_type, _name, _extensibility) enum _name : _type _name; enum SWIFT_ENUM_ATTR(_extensibility) SWIFT_ENUM_EXTRA _name : _type
# if __has_feature(generalized_swift_name)
#  define SWIFT_ENUM_NAMED(_type, _name, SWIFT_NAME, _extensibility) enum _name : _type _name SWIFT_COMPILE_NAME(SWIFT_NAME); enum SWIFT_COMPILE_NAME(SWIFT_NAME) SWIFT_ENUM_ATTR(_extensibility) SWIFT_ENUM_EXTRA _name : _type
# else
#  define SWIFT_ENUM_NAMED(_type, _name, SWIFT_NAME, _extensibility) SWIFT_ENUM(_type, _name, _extensibility)
# endif
#endif
#if !defined(SWIFT_UNAVAILABLE)
# define SWIFT_UNAVAILABLE __attribute__((unavailable))
#endif
#if !defined(SWIFT_UNAVAILABLE_MSG)
# define SWIFT_UNAVAILABLE_MSG(msg) __attribute__((unavailable(msg)))
#endif
#if !defined(SWIFT_AVAILABILITY)
# define SWIFT_AVAILABILITY(plat, ...) __attribute__((availability(plat, __VA_ARGS__)))
#endif
#if !defined(SWIFT_WEAK_IMPORT)
# define SWIFT_WEAK_IMPORT __attribute__((weak_import))
#endif
#if !defined(SWIFT_DEPRECATED)
# define SWIFT_DEPRECATED __attribute__((deprecated))
#endif
#if !defined(SWIFT_DEPRECATED_MSG)
# define SWIFT_DEPRECATED_MSG(...) __attribute__((deprecated(__VA_ARGS__)))
#endif
#if __has_feature(attribute_diagnose_if_objc)
# define SWIFT_DEPRECATED_OBJC(Msg) __attribute__((diagnose_if(1, Msg, "warning")))
#else
# define SWIFT_DEPRECATED_OBJC(Msg) SWIFT_DEPRECATED_MSG(Msg)
#endif
#if !defined(IBSegueAction)
# define IBSegueAction
#endif
#if __has_feature(modules)
#if __has_warning("-Watimport-in-framework-header")
#pragma clang diagnostic ignored "-Watimport-in-framework-header"
#endif
@import Foundation;
@import ObjectiveC;
@import os.log;
#endif

#import <WireSystem/WireSystem.h>

#pragma clang diagnostic ignored "-Wproperty-attribute-mismatch"
#pragma clang diagnostic ignored "-Wduplicate-method-arg"
#if __has_warning("-Wpragma-clang-attribute")
# pragma clang diagnostic ignored "-Wpragma-clang-attribute"
#endif
#pragma clang diagnostic ignored "-Wunknown-pragmas"
#pragma clang diagnostic ignored "-Wnullability"

#if __has_attribute(external_source_symbol)
# pragma push_macro("any")
# undef any
# pragma clang attribute push(__attribute__((external_source_symbol(language="Swift", defined_in="WireSystem",generated_declaration))), apply_to=any(function,enum,objc_interface,objc_category,objc_protocol))
# pragma pop_macro("any")
#endif


typedef SWIFT_ENUM(uint8_t, Environment, open) {
  EnvironmentAppStore = 0,
  EnvironmentInternal = 1,
  EnvironmentDebug = 2,
  EnvironmentDevelop = 3,
  EnvironmentUnknown = 4,
};


/// Opaque token to unregister observers
SWIFT_CLASS_NAMED("LogHookToken")
@interface ZMSLogLogHookToken : NSObject
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end


/// This is used to highlight memory leaks of critical objects.
/// In the code, add objects to the debugger by calling <code>MemoryReferenceDebugger.add</code>.
/// When running tests, you should check that <code>MemoryReferenceDebugger.aliveObjects</code> is empty
SWIFT_CLASS("_TtC10WireSystem23MemoryReferenceDebugger")
@interface MemoryReferenceDebugger : NSObject
+ (void)register:(NSObject * _Nullable)object line:(NSUInteger)line file:(char const * _Nonnull)file;
+ (void)reset;
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSArray * _Nonnull aliveObjects;)
+ (NSArray * _Nonnull)aliveObjects SWIFT_WARN_UNUSED_RESULT;
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSString * _Nonnull aliveObjectsDescription;)
+ (NSString * _Nonnull)aliveObjectsDescription SWIFT_WARN_UNUSED_RESULT;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end


/// A logging facility based on tags to switch on and off certain logs
/// note:
///
/// Usage. Add:
/// \code
/// private let zmLog = ZMLog(tag: "Networking")
///
/// \endcodeat the top of your .swift file and log with:
/// \code
/// zmLog.debug("Debug information")
/// zmLog.warn("A serious warning!")
///
/// \endcode
SWIFT_CLASS("_TtC10WireSystem6ZMSLog")
@interface ZMSLog : NSObject
- (nonnull instancetype)initWithTag:(NSString * _Nonnull)tag OBJC_DESIGNATED_INITIALIZER;
/// Wait for all log operations to be completed
+ (void)sync;
- (nonnull instancetype)init SWIFT_UNAVAILABLE;
+ (nonnull instancetype)new SWIFT_UNAVAILABLE_MSG("-init is unavailable");
@end



@interface ZMSLog (SWIFT_EXTENSION(WireSystem))
+ (void)logWithLevel:(ZMLogLevel_t)level message:(SWIFT_NOESCAPE NSString * _Nonnull (^ _Nonnull)(void))message tag:(NSString * _Nullable)tag file:(NSString * _Nonnull)file line:(NSUInteger)line;
@end


@class ZMSLogEntry;

@interface ZMSLog (SWIFT_EXTENSION(WireSystem))
/// Adds a log hook
+ (ZMSLogLogHookToken * _Nonnull)addEntryHookWithLogHook:(void (^ _Nonnull)(ZMLogLevel_t, NSString * _Nullable, ZMSLogEntry * _Nonnull, BOOL))logHook SWIFT_WARN_UNUSED_RESULT;
/// Adds a log hook without locking
+ (ZMSLogLogHookToken * _Nonnull)nonLockingAddEntryHookWithLogHook:(void (^ _Nonnull)(ZMLogLevel_t, NSString * _Nullable, ZMSLogEntry * _Nonnull, BOOL))logHook SWIFT_WARN_UNUSED_RESULT;
/// Remove a log hook
+ (void)removeLogHookWithToken:(ZMSLogLogHookToken * _Nonnull)token;
/// Remove all log hooks
+ (void)removeAllLogHooks;
@end



@interface ZMSLog (SWIFT_EXTENSION(WireSystem))
/// Start recording
+ (void)startRecordingWithIsInternal:(BOOL)isInternal;
/// Stop recording logs and discard cache
+ (void)stopRecording;
@end


@interface ZMSLog (SWIFT_EXTENSION(WireSystem))
/// Sets the minimum logging level for the tag
/// note:
/// switches to the log queue
+ (void)setWithLevel:(ZMLogLevel_t)level tag:(NSString * _Nonnull)tag;
/// Gets the minimum logging level for the tag
/// note:
/// switches to the log queue
+ (ZMLogLevel_t)getLevelWithTag:(NSString * _Nonnull)tag SWIFT_WARN_UNUSED_RESULT;
/// Gets the minimum logging level for the tag
/// note:
/// Does not switch to the log queue
+ (ZMLogLevel_t)getLevelNoLockWithTag:(NSString * _Nonnull)tag SWIFT_WARN_UNUSED_RESULT;
/// Registers a tag for logging
/// note:
/// Does not switch to the log queue
+ (void)registerWithTag:(NSString * _Nonnull)tag;
+ (os_log_t _Nonnull)loggerWithTag:(NSString * _Nullable)tag SWIFT_WARN_UNUSED_RESULT SWIFT_AVAILABILITY(ios,introduced=10);
/// Get all tags
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSArray<NSString *> * _Nonnull allTags;)
+ (NSArray<NSString *> * _Nonnull)allTags SWIFT_WARN_UNUSED_RESULT;
@end


@interface ZMSLog (SWIFT_EXTENSION(WireSystem))
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSData * _Nullable previousLog;)
+ (NSData * _Nullable)previousLog SWIFT_WARN_UNUSED_RESULT;
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSData * _Nullable currentLog;)
+ (NSData * _Nullable)currentLog SWIFT_WARN_UNUSED_RESULT;
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSURL * _Nullable previousLogPath;)
+ (NSURL * _Nullable)previousLogPath SWIFT_WARN_UNUSED_RESULT;
SWIFT_CLASS_PROPERTY(@property (nonatomic, class, readonly, copy) NSURL * _Nullable currentLogPath;)
+ (NSURL * _Nullable)currentLogPath SWIFT_WARN_UNUSED_RESULT;
+ (void)clearLogs;
+ (void)switchCurrentLogToPrevious;
@end


/// Represents an entry to be logged.
SWIFT_CLASS("_TtC10WireSystem11ZMSLogEntry")
@interface ZMSLogEntry : NSObject
@property (nonatomic, readonly, copy) NSString * _Nonnull text;
@property (nonatomic, readonly, copy) NSDate * _Nonnull timestamp;
- (nonnull instancetype)initWithText:(NSString * _Nonnull)text timestamp:(NSDate * _Nonnull)timestamp OBJC_DESIGNATED_INITIALIZER;
- (nonnull instancetype)init SWIFT_UNAVAILABLE;
+ (nonnull instancetype)new SWIFT_UNAVAILABLE_MSG("-init is unavailable");
@end

#if __has_attribute(external_source_symbol)
# pragma clang attribute pop
#endif
#pragma clang diagnostic pop
#endif
