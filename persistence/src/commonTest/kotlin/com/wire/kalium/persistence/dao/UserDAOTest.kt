package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDAOTest : BaseDatabaseTest() {

    val user1 = newUserEntity(id = "1")
    val user2 = newUserEntity(id = "2")
    val user3 = newUserEntity(id = "3")

    lateinit var db: Database

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenUser_ThenUserCanBeInserted() = runTest {
        db.userDAO.insertUser(user1)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(result, user1)
    }

    @Test
    fun givenListOfUsers_ThenMultipleUsersCanBeInsertedAtOnce() = runTest {
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        val result1 = db.userDAO.getUserByQualifiedID(user1.id).first()
        val result2 = db.userDAO.getUserByQualifiedID(user2.id).first()
        val result3 = db.userDAO.getUserByQualifiedID(user3.id).first()
        assertEquals(result1, user1)
        assertEquals(result2, user2)
        assertEquals(result3, user3)
    }

    @Test
    fun givenExistingUser_ThenUserCanBeDeleted() = runTest {
        db.userDAO.insertUser(user1)
        db.userDAO.deleteUserByQualifiedID(user1.id)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingUser_ThenUserCanBeUpdated() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(user1.id, "John Doe", "johndoe", "email1", "phone1", 1,
            "team", "preview1", "complete1")
        db.userDAO.updateUser(updatedUser1)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(result, updatedUser1)
    }

    @Test
    fun givenListOfUsers_ThenUserCanBeQueriedByName() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(user1.id, "John Doe", "johndoe", "email1", "phone1", 1,
            "team", "preview1", "complete1")

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

    @Test
    fun givenRetrievedUser_ThenUpdatesArePropagatedThroughFlow() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(user1.id, "John Doe", "johndoe", "email1", "phone1", 1,
            "team", "preview1", "complete1")

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByUserEmail_ThenResultsIsEqualToThatUser() = runTest {
        //given
        val user1 =
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
                previewAssetId = "preview1",
                completeAssetId = "complete1"
            )
        val user2 =
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            )
        val user3 =
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(user2.email!!).first()
        //then
        assertEquals(searchResult, listOf(user2))
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByName_ThenResultsIsEqualToThatUser() = runTest {
        //given
        val user1 =
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
                previewAssetId = "preview1",
                completeAssetId = "complete1"
            )
        val user2 =
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            )
        val user3 =
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(user3.handle!!).first()
        //then
        assertEquals(searchResult, listOf(user3))
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByHandle_ThenResultsIsEqualToThatUser() = runTest {
        //given
        val user1 =
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
                previewAssetId = "preview1",
                completeAssetId = "complete1"
            )
        val user2 =
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            )
        val user3 =
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(user1.name!!).first()
        //then
        assertEquals(searchResult, listOf(user1))
    }

    @Test
    fun givenAExistingUsersWithCommonEmailPrefix_WhenQueriedWithThatEmailPrefix_ThenResultIsEqualToTheUsersWithCommonEmailPrefix() =
        runTest {
            //given
            val commonEmailPrefix = "commonEmail"

            val commonEmailUsers = listOf(
                UserEntity(
                    id = QualifiedID("1", "wire.com"),
                    name = "testName1",
                    handle = "testHandle1",
                    email = commonEmailPrefix + "1@wire.com",
                    phone = "testPhone1",
                    accentId = 1,
                    team = "testTeam1",
                    previewAssetId = "preview1",
                    completeAssetId = "complete1"
                ),
                UserEntity(
                    id = QualifiedID("2", "wire.com"),
                    name = "testName2",
                    handle = "testHandle2",
                    email = commonEmailPrefix + "2@wire.com",
                    phone = "testPhone2",
                    accentId = 2,
                    team = "testTeam2",
                    previewAssetId = "preview2",
                    completeAssetId = "complete2"
                ),
                UserEntity(
                    id = QualifiedID("3", "wire.com"),
                    name = "testName3",
                    handle = "testHandle3",
                    email = commonEmailPrefix + "3@wire.com",
                    phone = "testPhone3",
                    accentId = 3,
                    team = "testTeam3",
                    previewAssetId = "preview3",
                    completeAssetId = "complete3"
                )
            )
            val notCommonEmailUsers = listOf(
                UserEntity(
                    id = QualifiedID("4", "wire.com"),
                    name = "testName4",
                    handle = "testHandle4",
                    email = "someDifferentEmail1@wire.com",
                    phone = "testPhone4",
                    accentId = 4,
                    team = "testTeam4",
                    previewAssetId = "preview4",
                    completeAssetId = "complete4"
                ),
                UserEntity(
                    id = QualifiedID("5", "wire.com"),
                    name = "testName5",
                    handle = "testHandle5",
                    email = "someDifferentEmail2@wire.com",
                    phone = "testPhone5",
                    accentId = 5,
                    team = "testTeam5",
                    previewAssetId = "preview5",
                    completeAssetId = "complete5"
                )
            )
            val mockUsers = commonEmailUsers + notCommonEmailUsers

            db.userDAO.insertUsers(mockUsers)
            //when
            val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(commonEmailPrefix).first()
            //then
            assertEquals(searchResult, commonEmailUsers)
        }

    //when entering
    @Test
    fun givenAExistingUsers_WhenQueriedWithNonExistingEmail_ThenReturnNoResults() = runTest {
        //given
        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName",
                handle = "testHandle",
                email = "testEmail@wire.com",
                phone = "testPhone",
                accentId = 1,
                team = "testTeam",
                previewAssetId = "preview1",
                completeAssetId = "complete1"
            ),
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        )
        db.userDAO.insertUsers(mockUsers)

        val nonExistingEmailQuery = "doesnotexist@wire.com"
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(nonExistingEmailQuery).first()
        //then
        assertTrue { searchResult.isEmpty() }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonEmailPrefix_ThenResultsUsersEmailContainsThatPrefix() = runTest {
        //given
        val commonEmailPrefix = "commonEmail"

        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = commonEmailPrefix + "1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
                previewAssetId = "preview1",
                completeAssetId = "complete1"
            ),
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = commonEmailPrefix + "2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = commonEmailPrefix + "3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        )
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(commonEmailPrefix).first()
        //then
        searchResult.forEach { userEntity ->
            assertContains(userEntity.email!!, commonEmailPrefix)
        }
    }


    @Test
    fun givenAExistingUsers_whenQueriedWithCommonHandlePrefix_ThenResultsUsersHandleContainsThatPrefix() = runTest {
        //given
        val commonHandlePrefix = "commonHandle"

        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = commonHandlePrefix + "1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
                previewAssetId = "preview21",
                completeAssetId = "complete1"
            ),
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = commonHandlePrefix + "2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = commonHandlePrefix + "3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        )
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(commonHandlePrefix).first()
        //then
        searchResult.forEach { userEntity ->
            assertContains(userEntity.handle!!, commonHandlePrefix)
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonNamePrefix_ThenResultsUsersNameContainsThatPrefix() = runTest {
        //given
        val commonNamePrefix = "commonName"

        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = commonNamePrefix + "1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
                previewAssetId = "preview1",
                completeAssetId = "complete1"
            ),
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = commonNamePrefix + "2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
                previewAssetId = "preview2",
                completeAssetId = "complete2"
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = commonNamePrefix + "3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
                previewAssetId = "preview3",
                completeAssetId = "complete3"
            )
        )
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(commonNamePrefix).first()
        //then
        searchResult.forEach { userEntity ->
            assertContains(userEntity.name!!, commonNamePrefix)
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonPrefixForNameHandleAndEmail_ThenResultsUsersNameHandleAndEmailContainsThatPrefix() =
        runTest {
            //given
            val commonPrefix = "common"

            val mockUsers = listOf(
                UserEntity(
                    id = QualifiedID("1", "wire.com"),
                    name = commonPrefix + "name1",
                    handle = commonPrefix + "handle1",
                    email = commonPrefix + "Email1@wire.com",
                    phone = "testPhone1",
                    accentId = 1,
                    team = "testTeam1",
                    previewAssetId = "preview1",
                    completeAssetId = "complete1"
                ),
                UserEntity(
                    id = QualifiedID("2", "wire.com"),
                    name = commonPrefix + "name2",
                    handle = commonPrefix + "handle2",
                    email = commonPrefix + "Email2@wire.com",
                    phone = "testPhone2",
                    accentId = 2,
                    team = "testTeam2",
                    previewAssetId = "preview2",
                    completeAssetId = "complete2"
                ),
                UserEntity(
                    id = QualifiedID("3", "wire.com"),
                    name = commonPrefix + "name3",
                    handle = commonPrefix + "handle3",
                    email = commonPrefix + "Email3@wire.com",
                    phone = "testPhone3",
                    accentId = 3,
                    team = "testTeam3",
                    previewAssetId = "preview3",
                    completeAssetId = "complete3"
                )
            )
            db.userDAO.insertUsers(mockUsers)
            //when
            val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(commonPrefix).first()
            //then
            searchResult.forEach { userEntity ->
                assertContains(userEntity.email!!, commonPrefix)
                assertContains(userEntity.name!!, commonPrefix)
                assertContains(userEntity.handle!!, commonPrefix)
            }
        }

}
