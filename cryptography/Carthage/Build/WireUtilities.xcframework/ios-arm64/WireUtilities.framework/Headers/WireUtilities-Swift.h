// Generated by Apple Swift version 5.3.2 (swiftlang-1200.0.45 clang-1200.0.32.28)
#ifndef WIREUTILITIES_SWIFT_H
#define WIREUTILITIES_SWIFT_H
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
@import CoreGraphics;
@import Dispatch;
@import Foundation;
@import ObjectiveC;
@import UIKit;
@import WireSystem;
#endif

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
# pragma clang attribute push(__attribute__((external_source_symbol(language="Swift", defined_in="WireUtilities",generated_declaration))), apply_to=any(function,enum,objc_interface,objc_category,objc_protocol))
# pragma pop_macro("any")
#endif

@class ZMSDispatchGroup;

SWIFT_CLASS("_TtC13WireUtilities20DispatchGroupContext")
@interface DispatchGroupContext : NSObject
@property (nonatomic, readonly, copy) NSArray<ZMSDispatchGroup *> * _Nonnull groups;
- (nonnull instancetype)initWithGroups:(NSArray<ZMSDispatchGroup *> * _Nonnull)groups OBJC_DESIGNATED_INITIALIZER;
- (void)addGroup:(ZMSDispatchGroup * _Nonnull)group;
- (NSArray<ZMSDispatchGroup *> * _Nonnull)enterAllExcept:(ZMSDispatchGroup * _Nullable)group SWIFT_WARN_UNUSED_RESULT;
- (void)leaveGroups:(NSArray<ZMSDispatchGroup *> * _Nonnull)groups;
- (void)leaveAll;
- (nonnull instancetype)init SWIFT_UNAVAILABLE;
+ (nonnull instancetype)new SWIFT_UNAVAILABLE_MSG("-init is unavailable");
@end


SWIFT_CLASS("_TtC13WireUtilities18DispatchGroupQueue")
@interface DispatchGroupQueue : NSObject <ZMSGroupQueue>
@property (nonatomic, readonly, strong) dispatch_queue_t _Nonnull queue;
@property (nonatomic, readonly, strong) DispatchGroupContext * _Nonnull dispatchGroupContext;
- (nonnull instancetype)initWithQueue:(dispatch_queue_t _Nonnull)queue OBJC_DESIGNATED_INITIALIZER;
@property (nonatomic, readonly, strong) ZMSDispatchGroup * _Null_unspecified dispatchGroup;
- (void)add:(ZMSDispatchGroup * _Nonnull)group;
- (void)performGroupedBlock:(void (^ _Nonnull)(void))block;
- (nonnull instancetype)init SWIFT_UNAVAILABLE;
+ (nonnull instancetype)new SWIFT_UNAVAILABLE_MSG("-init is unavailable");
@end


SWIFT_CLASS("_TtC13WireUtilities35ExtremeCombiningCharactersValidator")
@interface ExtremeCombiningCharactersValidator : NSObject
+ (BOOL)validateValue:(id _Nullable * _Null_unspecified)ioValue error:(NSError * _Nullable * _Nullable)error;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end



@interface NSFileManager (SWIFT_EXTENSION(WireUtilities))
/// Moves the content of the folder recursively to another folder.
/// If the destionation folder does not exists, it creates it.
/// If it exists, it moves files and folders from the first folder to the second, then
/// deletes the first folder.
- (BOOL)moveFolderRecursivelyFrom:(NSURL * _Nonnull)source to:(NSURL * _Nonnull)destination overwriteExistingFiles:(BOOL)overwriteExistingFiles error:(NSError * _Nullable * _Nullable)error;
/// Copies the content of the folder recursively to another folder.
/// If the destionation folder does not exists, it creates it.
- (BOOL)copyFolderRecursivelyFrom:(NSURL * _Nonnull)source to:(NSURL * _Nonnull)destination overwriteExistingFiles:(BOOL)overwriteExistingFiles error:(NSError * _Nullable * _Nullable)error;
@end



@interface NSData (SWIFT_EXTENSION(WireUtilities))
/// Returns whether the data represents animated GIF
/// \param data image data
///
///
/// returns:
/// returns turn if the data is GIF and number of images > 1
- (BOOL)isDataAnimatedGIF SWIFT_WARN_UNUSED_RESULT;
@end



@interface NSNumber (SWIFT_EXTENSION(WireUtilities))
+ (uint32_t)secureRandomNumberWithUpperBound:(uint32_t)upperBound SWIFT_WARN_UNUSED_RESULT;
@end



@interface NSSet<ObjectType> (SWIFT_EXTENSION(WireUtilities))
- (NSSet * _Nonnull)union:(NSSet * _Nonnull)s SWIFT_WARN_UNUSED_RESULT;
@property (nonatomic, readonly) BOOL isEmpty;
@end



@interface NSString (SWIFT_EXTENSION(WireUtilities))
@property (nonatomic, readonly, strong) NSString * _Nonnull stringByRemovingExtremeCombiningCharacters;
@end


@interface NSUUID (SWIFT_EXTENSION(WireUtilities))
/// Returns whether this UUID is of Type 1
@property (nonatomic, readonly) BOOL isType1UUID;
/// Returns the type 1 timestamp
///
/// returns:
/// NSDate, or <code>nil</code> if the NSUUID is not of Type 1
@property (nonatomic, readonly, copy) NSDate * _Nullable type1Timestamp;
/// Returns the comparison result for this NSUUID of type 1 and another NSUUID of type 1
/// requires:
/// will assert if any UUID is not of type 1
- (enum NSComparisonResult)compareWithType1UUID:(NSUUID * _Nonnull)type1UUID SWIFT_WARN_UNUSED_RESULT;
+ (NSUUID * _Nonnull)timeBasedUUID SWIFT_WARN_UNUSED_RESULT;
@end


SWIFT_CLASS("_TtC13WireUtilities40SelfUnregisteringNotificationCenterToken")
@interface SelfUnregisteringNotificationCenterToken : NSObject
- (nonnull instancetype)init:(id _Nonnull)token OBJC_DESIGNATED_INITIALIZER;
- (nonnull instancetype)init SWIFT_UNAVAILABLE;
+ (nonnull instancetype)new SWIFT_UNAVAILABLE_MSG("-init is unavailable");
@end


SWIFT_CLASS("_TtC13WireUtilities21StringLengthValidator")
@interface StringLengthValidator : NSObject
+ (BOOL)validateValue:(id _Nullable * _Null_unspecified)ioValue minimumStringLength:(uint32_t)minimumStringLength maximumStringLength:(uint32_t)maximumStringLength maximumByteLength:(uint32_t)maximumByteLength error:(NSError * _Nullable * _Nullable)error;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end


/// Object that can be torn down when not needed anymore
SWIFT_PROTOCOL("_TtP13WireUtilities15TearDownCapable_")
@protocol TearDownCapable <NSObject>
- (void)tearDown;
@end



@interface UIColor (SWIFT_EXTENSION(WireUtilities))
/// Pass in amount of 0 for self, 1 is the other color
/// \param color other color to mix
///
/// \param progress amount of other color
///
///
/// returns:
/// the mixed color
- (UIColor * _Nonnull)mix:(UIColor * _Nonnull)color amount:(CGFloat)progress SWIFT_WARN_UNUSED_RESULT;
@end



SWIFT_CLASS("_TtC13WireUtilities9UTIHelper")
@interface UTIHelper : NSObject
+ (BOOL)conformsToImageTypeWithUti:(NSString * _Nonnull)uti SWIFT_WARN_UNUSED_RESULT;
+ (BOOL)conformsToVectorTypeWithUti:(NSString * _Nonnull)uti SWIFT_WARN_UNUSED_RESULT;
+ (BOOL)conformsToJsonTypeWithUti:(NSString * _Nonnull)uti SWIFT_WARN_UNUSED_RESULT;
+ (NSString * _Nullable)convertToUtiWithMime:(NSString * _Nonnull)mime SWIFT_WARN_UNUSED_RESULT;
+ (NSString * _Nullable)convertToMimeWithUti:(NSString * _Nonnull)uti SWIFT_WARN_UNUSED_RESULT;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end


SWIFT_CLASS("_TtC13WireUtilities15UnownedNSObject")
@interface UnownedNSObject : NSObject
@property (nonatomic, weak) NSObject * _Nullable unbox;
- (nonnull instancetype)init:(NSObject * _Nonnull)unbox OBJC_DESIGNATED_INITIALIZER;
@property (nonatomic, readonly) BOOL isValid;
- (nonnull instancetype)init SWIFT_UNAVAILABLE;
+ (nonnull instancetype)new SWIFT_UNAVAILABLE_MSG("-init is unavailable");
@end


SWIFT_CLASS("_TtC13WireUtilities22ZMAccentColorValidator")
@interface ZMAccentColorValidator : NSObject
+ (BOOL)validateValue:(id _Nullable * _Null_unspecified)ioValue error:(NSError * _Nullable * _Nullable)error;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end


SWIFT_CLASS("_TtC13WireUtilities23ZMEmailAddressValidator")
@interface ZMEmailAddressValidator : NSObject
+ (BOOL)validateValue:(id _Nullable * _Null_unspecified)ioValue error:(NSError * _Nullable * _Nullable)error;
+ (BOOL)isValidEmailAddress:(NSString * _Nonnull)emailAddress SWIFT_WARN_UNUSED_RESULT;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end

typedef SWIFT_ENUM(NSInteger, ZMManagedObjectValidationErrorCode, open) {
  ZMManagedObjectValidationErrorCodeTooLong = 0,
  ZMManagedObjectValidationErrorCodeTooShort = 1,
  ZMManagedObjectValidationErrorCodeEmailAddressIsInvalid = 2,
  ZMManagedObjectValidationErrorCodePhoneNumberContainsInvalidCharacters = 3,
};
static NSString * _Nonnull const ZMManagedObjectValidationErrorCodeDomain = @"WireUtilities.ZMManagedObjectValidationErrorCode";


SWIFT_CLASS("_TtC13WireUtilities22ZMPhoneNumberValidator")
@interface ZMPhoneNumberValidator : NSObject
+ (BOOL)validateValue:(id _Nullable * _Null_unspecified)ioValue error:(NSError * _Nullable * _Nullable)error;
+ (BOOL)isValidPhoneNumber:(NSString * _Nonnull)phoneNumber SWIFT_WARN_UNUSED_RESULT;
+ (NSString * _Nullable)validatePhoneNumber:(NSString * _Nonnull)phoneNumber SWIFT_WARN_UNUSED_RESULT;
- (nonnull instancetype)init OBJC_DESIGNATED_INITIALIZER;
@end

#if __has_attribute(external_source_symbol)
# pragma clang attribute pop
#endif
#pragma clang diagnostic pop
#endif
