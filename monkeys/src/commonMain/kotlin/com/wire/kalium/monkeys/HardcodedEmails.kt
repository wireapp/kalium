/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.monkeys

import com.wire.kalium.logic.data.user.UserId

private val userLoggingInOnVitorsDevice = "smoketester+16e262cd2d@wire.com"
val password = "Aqa123456!"
val domain = "anta.wire.link"

data class UserData(val email: String, val password: String, val userId: UserId)

private val hardcodedUsers = setOf(
    Pair("smoketester+80bcb20c42@wire.com", "3fcadd87-76b0-4d43-9d54-5146252749df"),
    Pair("smoketester+d047d46669@wire.com", "3a770fb9-cf43-4a7b-bc14-2c04b0edc3e7"),
    Pair("smoketester+dc9d41dd3a@wire.com", "977b0fd6-4a6f-4b41-ab1f-6ca91a7751db"),
    Pair("smoketester+6769ebf68f@wire.com", "e64c1dc4-c351-4a3c-837c-5e94184718f0"),
    Pair("smoketester+ae8b0e93e6@wire.com", "fef1abfe-4880-4130-af5d-7c8d6cf142d9"),
    Pair("smoketester+403dd1261c@wire.com", "a7634d57-ad2a-4f12-ba94-d4451ce26eed"),
    Pair("smoketester+0352f35116@wire.com", "04940186-3333-4353-a8cb-ab70d927f0e6"),
    Pair("smoketester+35c9ed514f@wire.com", "db9867f7-f42d-4e3c-8877-049d9aeee856"),
    Pair("smoketester+cd4a4ec844@wire.com", "780ed598-283e-44cc-93b5-13df51bc9cc3"),
    Pair("smoketester+b28122c999@wire.com", "fcc5dc2c-d657-4865-828e-74b83d71c3da"),
    Pair("smoketester+1ffb651317@wire.com", "1f2da0ef-0a21-453b-b17c-ada8c4518b72"),
    Pair("smoketester+1971ff7d1e@wire.com", "ecf39d25-a7f3-4379-a9e0-aa815c90be12"),
    Pair("smoketester+c0627b55a5@wire.com", "af042cf8-aa8a-48d5-8cd9-6f235d9ba596"),
    Pair("smoketester+469e75dcca@wire.com", "bb9c7984-6818-441d-a04d-94c26ba2bc2c"),
    Pair("smoketester+873fd5c042@wire.com", "09cbf491-70d6-4c55-a627-70bf463b31ec"),
    Pair("smoketester+ec241f9b16@wire.com", "0abb5c1d-333c-4f5c-a179-99b7ce001642"),
    Pair("smoketester+3c32116c65@wire.com", "5a72ba5a-5b4f-487a-b88a-265bca2e050b"),
    Pair("smoketester+84d5e04165@wire.com", "c5b7e6ef-a6e0-4f56-9924-befd03bb1b33"),
    Pair("smoketester+ae4319f600@wire.com", "9d3dd1be-797a-450d-991a-2704c13c2cfd"),
    Pair("smoketester+8f607b96dd@wire.com", "1da0422d-89e7-440f-a572-dea6ed4a9b44"),
    Pair("smoketester+ebd600259a@wire.com", "13ef0927-da9c-40c4-ac43-64975de886d0"),
    Pair("smoketester+7a9c0ca4f5@wire.com", "ac2fdbbf-71f7-4825-be1a-1f573d4b90d0"),
    Pair("smoketester+a5f24cb84d@wire.com", "0fa405fc-1b37-434e-aff2-8b933977d310"),
    Pair("smoketester+f6d802a327@wire.com", "7d8383a5-7f10-44d1-81bf-403cc0dee686"),
    Pair("smoketester+2c886aa3de@wire.com", "361f3184-f1dd-4107-a357-a8ab0f050de0"),
    Pair("smoketester+12f32f55b7@wire.com", "8de0b3b6-db4d-4135-b9a4-bed6863162a5"),
    Pair("smoketester+c456886a38@wire.com", "a758b2e9-c6b0-487c-8914-d42ade467d9b"),
    Pair("smoketester+3941f5e3f4@wire.com", "977d5c60-95ad-41a1-a521-509ceec87f6f"),
    Pair("smoketester+3411095563@wire.com", "c6bfb129-9f24-43b9-af38-fbafa661d61a"),
    Pair("smoketester+f0296233f3@wire.com", "565b4d76-c332-4baf-8174-463da03204de"),
    Pair("smoketester+b0f47a1f4c@wire.com", "b8f2cf76-4de2-4413-9254-b886bb933315"),
    Pair("smoketester+850bda1681@wire.com", "aa624e42-0b77-49c1-970c-1cc0413ccd8a"),
    Pair("smoketester+f4c3f5cfcc@wire.com", "701863f7-fa66-405c-89ae-c1b0ab0935f1"),
    Pair("smoketester+0d159e0b1a@wire.com", "dacaab98-41df-4da7-90e6-56bac39fbe1b"),
    Pair("smoketester+dbe02484c2@wire.com", "a778d138-9d67-47de-8b95-d0618f7c8a89"),
    Pair("smoketester+8170b04e78@wire.com", "c7e53b38-dc63-42e7-82ba-086bf119c915"),
    Pair("smoketester+387c5f0686@wire.com", "5b8bab1d-213a-4bdb-9141-7ea65c4c488a"),
    Pair("smoketester+0fcc388f33@wire.com", "b12badbc-a287-464e-bc56-36221abffcfd"),
    Pair("smoketester+3b7af28306@wire.com", "e4c279c3-bb98-47ce-9395-af0dbe77d7d5"),
    Pair("smoketester+450b6825e4@wire.com", "e73bd6ac-deb8-4d9e-8b07-91d9a1a94576"),
    Pair("smoketester+fe77ca2c97@wire.com", "617162aa-b49f-4f58-b21a-a478da9d071c"),
    Pair("smoketester+fc5accb1cc@wire.com", "ea68cc9a-7850-488b-b83c-f5e1be4fc1a2"),
    Pair("smoketester+72a40f94f6@wire.com", "fbf08bab-2b63-40d8-a334-96412af48dfb"),
    Pair("smoketester+5dc36398c7@wire.com", "593a6cc9-4bce-4915-aa46-3becb719880d"),
    Pair("smoketester+4c34af974f@wire.com", "bdb73296-ef08-4818-9bbe-b9da7bc51040"),
    Pair("smoketester+541ee1de4a@wire.com", "46eecdac-4891-41ea-9f46-6d5d64082de6"),
    Pair("smoketester+f37773166c@wire.com", "5885d776-0817-4736-84d5-519f9b00bcae"),
    Pair("smoketester+7483fb03f0@wire.com", "2ea9ef54-2654-4178-a04d-0e0495295aa3"),
    Pair("smoketester+30f8f2a8ba@wire.com", "36f0c457-83c8-42d2-97c0-91c7d5e72b09"),
    Pair("smoketester+28a4cee765@wire.com", "b50131e9-7332-4252-8c65-d5f20cc2cbe6"),
    Pair("smoketester+7c06cb6967@wire.com", "30287bd4-74cf-47fa-928f-398e634706f9"),
    Pair("smoketester+8fc3ccd342@wire.com", "7ee040fe-64c7-4233-a72f-ee374eb9dcc6"),
    Pair("smoketester+4b9fd855f3@wire.com", "a61a33a9-7d91-4042-be3f-f9b95c59e31b"),
    Pair("smoketester+a9f3593411@wire.com", "0f023014-bc77-4c8c-ade0-27f87b29be16"),
    Pair("smoketester+def18602a3@wire.com", "5910745a-306a-4fc5-a6ad-e24218870048"),
    Pair("smoketester+ea232deb55@wire.com", "b81b84b5-22b4-4714-875b-a4d7b8b4dbc8"),
    Pair("smoketester+075cd685a0@wire.com", "0bb07aed-4dcb-4d25-859b-8fd61a2700df"),
    Pair("smoketester+c015b2b4ce@wire.com", "c987ed26-d91d-4b5c-a0a4-c0055ff5d04f"),
    Pair("smoketester+60ade59a8d@wire.com", "f89059a5-d103-4052-9c7c-4fb83fa4e793"),
    Pair("smoketester+115ae24bd7@wire.com", "8077c4f7-905b-4aa5-bedf-ef10eac88aae"),
    Pair("smoketester+5b340b58b0@wire.com", "ba905b43-82a6-4ef9-b89c-4df2c2759659"),
    Pair("smoketester+c26a7e8600@wire.com", "535090f7-8ce9-45cf-9f89-ba906757c7dd"),
    Pair("smoketester+0d3fb28fba@wire.com", "0075b797-7d01-4694-9305-cc0b6efcf08a"),
    Pair("smoketester+60c1b6604c@wire.com", "2cb85d1d-0857-4990-a359-ac6613476299"),
    Pair("smoketester+d253d139aa@wire.com", "f259ba6c-75cd-4e9e-a407-937a1da622be"),
    Pair("smoketester+b4e59073fc@wire.com", "f5e853f5-3823-4407-b1fa-5fe9aa88c7a8"),
    Pair("smoketester+19c73501da@wire.com", "21cfcb30-b1b8-46a2-a0b4-a27a07601a61"),
    Pair("smoketester+d3e70a7bd6@wire.com", "5b38069c-210d-459d-9429-d07dd5929dd0"),
    Pair("smoketester+adf5c1cdd2@wire.com", "5fdfe7da-bbc8-406b-a43c-913012da4588"),
    Pair("smoketester+9a9a402b7e@wire.com", "30c8468f-2c31-4754-a2ec-d0917b453310"),
    Pair("smoketester+eb2bed696e@wire.com", "1806269c-e331-4e2c-ad69-1f03bc355493"),
    Pair("smoketester+0df504aefe@wire.com", "b97c2e0f-64a0-4a9b-85ea-fa66f6853289"),
    Pair("smoketester+b6ab652aa9@wire.com", "cbf84bf8-7c0c-4e97-bc80-9c1042483cd3"),
    Pair("smoketester+6a621b760b@wire.com", "0e29bcd4-0eec-43fe-9bd8-08aca386eb8c"),
    Pair("smoketester+3b877f9a09@wire.com", "8e9c7f93-0cb3-4df5-a43a-b4cdde099fd7"),
    Pair("smoketester+48d735e6fd@wire.com", "54988fed-07fa-40a3-9bd9-3e4d51a356bb"),
    Pair("smoketester+905e6cc93a@wire.com", "c782328b-4c65-4f28-9b56-27d843228d70"),
    Pair("smoketester+bfa15988ee@wire.com", "1797cb21-64ba-4917-9928-6f7315049639"),
    Pair("smoketester+fbf91f2f41@wire.com", "55717d69-da23-4716-abb4-ec4bef058f40"),
    Pair("smoketester+59220cdda9@wire.com", "d526bfdc-e3e4-4817-a196-83c592c932d0"),
    Pair("smoketester+f59d95fbbe@wire.com", "0db95f2c-ef2a-41ca-8925-a40a83171372"),
    Pair("smoketester+b4c68e7469@wire.com", "ec67737c-24b4-4732-8599-066cbd19ca35"),
    Pair("smoketester+cb606791d9@wire.com", "b08bbfda-50bb-4051-8923-d5ca8e4c7159"),
    Pair("smoketester+9a7349fcd9@wire.com", "340e1d38-8a9e-4934-a5b0-8aeb967663a8"),
    Pair("smoketester+289d02593e@wire.com", "768bdd65-8e77-4f34-9882-f8742f171600"),
    Pair("smoketester+5f97c9b1ec@wire.com", "a26095df-4854-4592-b3e1-9d9774711904"),
    Pair("smoketester+c3225e76aa@wire.com", "d2cebd1b-f37d-4f32-81cc-359bd5c8b183"),
    Pair("smoketester+9a67864131@wire.com", "b5354d6d-8f11-413d-9d1d-359f87210dee"),
    Pair("smoketester+db75215e41@wire.com", "4b652c57-7f56-4a64-abd0-f2c16a884511"),
    Pair("smoketester+328bbf0cc2@wire.com", "4950d736-1500-4064-8d44-a1d9505a7a60"),
    Pair("smoketester+aff79f2a41@wire.com", "3bb3cbec-f8f0-4ae7-8d0b-00a0ebc88087"),
    Pair("smoketester+551941d01d@wire.com", "4deb043c-a9a6-42a1-afc0-1d28d7d5432e"),
    Pair("smoketester+afb7d38fb9@wire.com", "b809f603-6c0f-4e89-ae33-863c40e84f23"),
    Pair("smoketester+e16d37e75a@wire.com", "b058cbb0-851b-4936-8dcf-9e99007f659b"),
    Pair("smoketester+be84749b27@wire.com", "bced45e2-5c5c-473b-8f93-6fb84505f8de"),
    Pair("smoketester+3a184e637f@wire.com", "47c0b57a-993e-4d01-ae39-4b5c6e8dd29b"),
    Pair("smoketester+d3413d048a@wire.com", "0428a05e-16fd-47c9-a43f-dfe511d29390"),
    Pair("smoketester+9f211fcb90@wire.com", "7d774544-dc6e-4991-8c38-09fe7b6b330d"),
    Pair("smoketester+d177d2fa30@wire.com", "a512722b-032e-4289-bbe8-a82b4992000f"),
    Pair("smoketester+1a1e5f313e@wire.com", "84af30c4-7178-4396-acea-bb8460545301"),
    Pair("smoketester+608f1648e5@wire.com", "c0b16925-e2b0-4966-8eb1-db4fb2f81647"),
    Pair("smoketester+a5d0f97f3f@wire.com", "2bda553f-d8a2-41ab-9972-373b38bf09bf"),
    Pair("smoketester+489ee998e6@wire.com", "9edb7eb8-be63-4de4-b1ee-f6faba9fa109"),
    Pair("smoketester+51de176982@wire.com", "fbde5c58-a481-4de4-847c-949e2e3136b3"),
    Pair("smoketester+56db9022a8@wire.com", "765048c2-4f4e-4536-b17d-2780bc41a32d"),
    Pair("smoketester+95db23a8bb@wire.com", "1a9d8aa0-5ace-4baa-bf7b-31b2e477efb7"),
    Pair("smoketester+d8045bd06b@wire.com", "c50970c9-52cb-4087-8c1d-fe98f1046b5e"),
    Pair("smoketester+1c1d628b59@wire.com", "cd1918ed-c6ab-4bde-b9b0-30b11a2d69b6"),
    Pair("smoketester+73603349fe@wire.com", "a8feffa7-fb63-4cbf-87fd-065c34143d25"),
    Pair("smoketester+009fcca72a@wire.com", "78a12433-d06c-4510-beb1-11a10682041a"),
    Pair("smoketester+4ea8a2f438@wire.com", "f64877f0-284d-4320-86ae-42c05d93eac4"),
    Pair("smoketester+0da17c4678@wire.com", "10a2c98f-f4f5-454a-8902-7dae8318d829"),
    Pair("smoketester+3ca11021d3@wire.com", "ac5c0c42-3c19-42ea-88b8-b1327e922f1a"),
    Pair("smoketester+c3c09fb387@wire.com", "d85d328b-a9aa-4389-a340-e9dcd18f7380"),
    Pair("smoketester+0beb8cdc95@wire.com", "de969988-d5da-48d2-9573-1181ac18af30"),
    Pair("smoketester+06fb3db4bd@wire.com", "da617077-e826-48a2-8702-0e88340d4028"),
    Pair("smoketester+e9c3afe3ff@wire.com", "b9b6bd78-e8ce-41ef-bfdc-9f3f6a72af61"),
    Pair("smoketester+41b45dea78@wire.com", "d6675883-a2f4-4281-a9e1-f7a64b0c2b7e"),
    Pair("smoketester+b6200729d4@wire.com", "d4b559ae-6e81-4c13-9770-244429847bde"),
    Pair("smoketester+c27567e14d@wire.com", "344639c3-348c-4094-ba4f-7833f87cea49"),
    Pair("smoketester+9ea4caf163@wire.com", "a5dc7c31-1662-423b-8687-b1b7cb44f53a"),
    Pair("smoketester+cf42a65282@wire.com", "b641cea3-f42d-493d-9eba-374ff3bc7ca7"),
    Pair("smoketester+f79a0bcf9d@wire.com", "29173d8f-0246-47e2-b373-9726d0d5b2b9"),
    Pair("smoketester+823db73ee2@wire.com", "4839689c-adda-4512-8d25-06106ff52daf"),
    Pair("smoketester+29847dca9f@wire.com", "edac65ea-32a1-46f7-b9f1-069cefbb0952"),
    Pair("smoketester+b47ee1b961@wire.com", "c5c6ac81-e031-4143-a6f8-063637e201ca"),
    Pair("smoketester+8d49e1f4bd@wire.com", "351d05dc-b1dc-4273-acde-15339eeeecbd"),
    Pair("smoketester+98e21e38d1@wire.com", "ba8391a7-462b-4dae-b626-8e7be9a5723a"),
    Pair("smoketester+bb22b90d97@wire.com", "e4c23aa8-639f-4148-97de-d36254215ed3"),
    Pair("smoketester+85ec0db434@wire.com", "89667187-97d5-4ec7-8b63-bed732364233"),
    Pair("smoketester+4b08c9dcd3@wire.com", "58ce9635-e6ad-442c-9aaf-b5dc7c88e9b1"),
    Pair("smoketester+997f1dd827@wire.com", "96adcc2b-6558-448d-a194-a05e0fb4a6df"),
    Pair("smoketester+e3f71e1f4a@wire.com", "f52ea46d-946d-4e1c-98c1-65e0502d9058"),
    Pair("smoketester+3e27b149f0@wire.com", "e8631c81-60a4-40c6-8958-72ac2a32e9d1"),
    Pair("smoketester+4479bac7da@wire.com", "33d4984e-9d4d-4e0e-b3f9-f2c6c1c307a5"),
    Pair("smoketester+e1e1f56d18@wire.com", "d399863e-0ac4-4681-832d-d373325b1377"),
    Pair("smoketester+ff875ef4fe@wire.com", "be300c73-4b0d-4291-a0d7-0d984bde7e6f"),
    Pair("smoketester+69936c110f@wire.com", "f89f61f5-a668-4e35-abe2-07ca76ace163"),
    Pair("smoketester+a01bb2515a@wire.com", "50c9a639-2c92-47cc-af41-361d4eeb2d94"),
    Pair("smoketester+2847c74de0@wire.com", "4e1a0eee-b487-41e4-8df1-46a5c513529b"),
    Pair("smoketester+4c0d6d9549@wire.com", "21832269-737a-486e-9efe-eeba5cc66121"),
    Pair("smoketester+fd98d3710c@wire.com", "449a8a2a-93c3-4b8c-9f1d-33f828dbf1d3"),
    Pair("smoketester+563f3572c3@wire.com", "ed3922dd-f0f3-4e23-a226-8301fbf24ede"),
    Pair("smoketester+5fc8923bd5@wire.com", "1f121d35-a1b2-4e9e-804d-c3b69d4f9673"),
    Pair("smoketester+42543b2752@wire.com", "8b77826b-ee06-4947-b4de-71746f24da72"),
    Pair("smoketester+1f32bf1790@wire.com", "79704e85-a4f6-48d3-a071-b3cffb84974c"),
    Pair("smoketester+77a2e07719@wire.com", "a5ffe83f-a437-4c10-b27b-f9a911e5b503"),
    Pair("smoketester+1d12a76690@wire.com", "9c868c48-5512-4c37-a262-653e78a9a0ae"),
    Pair("smoketester+8ddfb795e2@wire.com", "66040d66-4e65-4250-878b-81f1b69fa0e3"),
    Pair("smoketester+91e01b7715@wire.com", "b260a912-ddf3-4242-8d7b-5a2cefaa86f7"),
    Pair("smoketester+1c3ed26724@wire.com", "2d3dd9f9-2a4d-44ad-8c4e-c33d541dc8b5"),
    Pair("smoketester+7c5dc21a7f@wire.com", "db3021b6-b242-4510-acf8-c6ed83243186"),
    Pair("smoketester+fe4ac7d7bc@wire.com", "c6992032-add5-471b-a848-7014161c54f9"),
    Pair("smoketester+9c82cb860f@wire.com", "4b2e8dda-25f2-424a-b2c1-f99a79ba1015"),
    Pair("smoketester+bd0a9e52b5@wire.com", "f45aedf4-fe52-4429-97e7-bc64652979d8"),
    Pair("smoketester+c92ef069dc@wire.com", "9547c8ed-81bb-4059-b94b-345cc3daff24"),
    Pair("smoketester+a3e7bfdd1d@wire.com", "71612446-209f-4789-a1c5-69f230d7d896"),
    Pair("smoketester+0c1597cdfb@wire.com", "aef38cc6-baeb-47a1-ae09-00997215a831"),
    Pair("smoketester+2b29bdc6b3@wire.com", "da56e58d-f221-4d35-b6c4-dda01a6a11c2"),
    Pair("smoketester+3981add208@wire.com", "46ab7f6f-5bf9-4f39-ab96-425e30379c9c"),
    Pair("smoketester+11c1780344@wire.com", "f0514982-37d5-42b1-b2eb-32a091ebcf61"),
    Pair("smoketester+b8d0244b7c@wire.com", "27ebbd8b-5771-4d04-bd91-15bbd32a9910"),
    Pair("smoketester+fa2a2f582d@wire.com", "f7b6c06c-3399-4c18-90be-0fcbdfc6ce7a"),
    Pair("smoketester+abc57875d6@wire.com", "278f918e-82c8-4486-8605-af4cbc240345"),
    Pair("smoketester+3629c9902a@wire.com", "452c8713-e8ef-4d0f-b09e-4ce8c5bd6a64"),
    Pair("smoketester+5a4eab020f@wire.com", "3ccbdb30-29ea-442a-808d-507b1610565a"),
    Pair("smoketester+09c0510b63@wire.com", "556327a8-ab18-4854-96b5-4b590324955a"),
    Pair("smoketester+e9822272d1@wire.com", "c7064689-020e-45f7-8cc9-34b0e6b90828"),
    Pair("smoketester+4e5949a2b2@wire.com", "603cb516-a566-4dea-a1c4-9ce18cb8c5f3"),
    Pair("smoketester+b2deba5f76@wire.com", "5073bd3c-163a-4102-869e-db9505d51b03"),
    Pair("smoketester+de7d7e75d2@wire.com", "e2705be1-5d94-4dea-a04c-f2befbc70c6c"),
    Pair("smoketester+80c3385a01@wire.com", "507bdaa7-1b60-4a41-8cd1-ef6b4cb6bbf3"),
    Pair("smoketester+e38d603c30@wire.com", "14613c82-0a77-4a29-a23e-d6c5d484cf71"),
    Pair("smoketester+7034c651d3@wire.com", "2aa5324e-ce51-47bb-a796-5acc579b072e"),
    Pair("smoketester+0e7a465fc4@wire.com", "3ead530e-66bf-4ead-9f4d-858a1ad4c512"),
    Pair("smoketester+4433c96a29@wire.com", "91e7a5a9-6f12-44a5-b1f4-cf1cfba488fd"),
    Pair("smoketester+6bba80cd0b@wire.com", "a47fbf1e-06b7-43fa-91b1-51d97b25d19e"),
    Pair("smoketester+1c0f53c4a1@wire.com", "2bf80391-5217-4ddc-80c4-b070508f965d"),
    Pair("smoketester+680fb0f717@wire.com", "aae02639-50ca-40d1-8699-e8ca80246924"),
    Pair("smoketester+8ce98e4472@wire.com", "9a363b9f-5567-4cde-b76d-c86de7a00c99"),
    Pair("smoketester+f89b3bfc34@wire.com", "61c60859-172f-4a1d-8a6b-c11e098e3db8"),
    Pair("smoketester+1a4b6ce4f3@wire.com", "a21834cf-b9d1-4709-99a7-f12039232dbc"),
    Pair("smoketester+d6a0794344@wire.com", "b29e505b-c1bb-42db-9a1a-d5005788825f"),
    Pair("smoketester+9f184ca6c6@wire.com", "8bbede12-5f95-401c-9728-189ed3c236be"),
    Pair("smoketester+02ae5e2226@wire.com", "e25da1a5-2991-41e8-a4ef-a93098652fe4"),
    Pair("smoketester+41cd255bbf@wire.com", "c9bebb7f-32d1-40c1-9d33-21c8d6cdd950")
)

private val team_200 = setOf(
    Pair("smoketester+9aacd22af3@wire.com", "22400589-1977-498e-9dc9-bca5cfbda2bd"),
    Pair("smoketester+5ebdb39835@wire.com", "708bb6c6-fcd6-4450-82aa-ce8dd7be3d07"),
    Pair("smoketester+ee6d37e1d0@wire.com", "248d89dd-2f0e-4155-bfab-efc07801fd85"),
    Pair("smoketester+69c1436a20@wire.com", "c5754f22-a8ee-4c32-a530-fe5b2ae1a9e2"),
    Pair("smoketester+8893765738@wire.com", "f38955af-5c6d-447d-b6c1-dba2e95e21e2"),
    Pair("smoketester+bded1c12cb@wire.com", "198744cc-b051-4a7a-8195-35c35c059598"),
    Pair("smoketester+a081981c24@wire.com", "1a106dba-b626-41a7-88c3-cfca871094b4"),
    Pair("smoketester+27ad1a5c81@wire.com", "44432238-c81b-4306-8a16-680dd7144f6b"),
    Pair("smoketester+43e1908dc5@wire.com", "d97b91cf-f45f-4ad1-a989-1291f07ea51b"),
    Pair("smoketester+d05ce27cab@wire.com", "4d6a1c3e-4af9-488c-9340-fcdad464c395"),
    Pair("smoketester+d1f8991200@wire.com", "69054832-2eee-41f3-a131-1e7a9cc9e3fb"),
    Pair("smoketester+6ef7ee997a@wire.com", "f55e57bc-4f74-4d25-931f-092f74699a75"),
    Pair("smoketester+78d21c4341@wire.com", "011d92c6-4cba-4257-b5bc-de51210e97a6"),
    Pair("smoketester+b30e334387@wire.com", "5565cd7d-c997-48ef-ba1b-276a397f1aa4"),
    Pair("smoketester+69c085608f@wire.com", "6ae32a1c-fefc-4a08-981d-19426c818d82"),
    Pair("smoketester+6041b60060@wire.com", "3ac0baf1-13cc-4265-90e5-f4cd958ca8ce"),
    Pair("smoketester+ded66b8e0c@wire.com", "bcd3241d-b7f5-4086-b20a-60f891223865"),
    Pair("smoketester+fbf4048a25@wire.com", "5c8dabe9-74ee-4b47-a52a-3494409714ef"),
    Pair("smoketester+08a69a6fd3@wire.com", "96688781-918e-4fd9-8fa8-083eb2cc24b9"),
    Pair("smoketester+68a0731091@wire.com", "2c7d551c-402a-4417-8f6f-2d14aff1f69e"),
    Pair("smoketester+aa7c4b98b4@wire.com", "662ad5f9-5614-4650-bd43-a83e2e290de8"),
    Pair("smoketester+41abb2b287@wire.com", "d16b7dce-d935-40ae-aced-01329f122773"),
    Pair("smoketester+497c15fe40@wire.com", "d6e20804-ac85-41e1-9747-3ff95e795217"),
    Pair("smoketester+00caab4a26@wire.com", "899a62b2-de94-4f63-8dd7-505a81de486c"),
    Pair("smoketester+99b18c2c41@wire.com", "3487d5aa-eac6-4b79-b658-77c2d32bfc4b"),
    Pair("smoketester+7d02411e8a@wire.com", "1a679be1-592c-483d-8c8e-23428aece135"),
    Pair("smoketester+b538b15399@wire.com", "8d8dba3e-f239-46a1-8a02-0e4c540c23ed"),
    Pair("smoketester+0449ffd9ae@wire.com", "a32bd05f-aa61-42c1-8706-9f959ab3b624"),
    Pair("smoketester+ef8fcf9a19@wire.com", "aa6ea217-8463-4547-bbad-4c11d5087351"),
    Pair("smoketester+b6333a83c8@wire.com", "a1747071-097f-42b8-aa15-b45cf4aabc99"),
    Pair("smoketester+1336bbf198@wire.com", "03950a8e-c048-46f9-bd77-3bde7da6919f"),
    Pair("smoketester+aeb26ac879@wire.com", "07d5288e-cc7d-41f9-90c0-418704e25fee"),
    Pair("smoketester+f41b1b7324@wire.com", "158e5e07-23e2-4710-b262-dde295595a2d"),
    Pair("smoketester+d3a8b96655@wire.com", "d3c1ccc2-7861-4f4d-83cc-aa1df1135ba1"),
    Pair("smoketester+58257a7075@wire.com", "e7965886-a789-4838-bfaa-11fc1dedea9b"),
    Pair("smoketester+2293a06e99@wire.com", "a61b4ee7-5d8b-4559-a167-d86d22de546f"),
    Pair("smoketester+9606032aa8@wire.com", "19d130d2-a18d-435e-8786-c02b11c3df81"),
    Pair("smoketester+453a7898d6@wire.com", "aa43d3b1-9862-4602-889a-3c735c3994b1"),
    Pair("smoketester+c642bbe88e@wire.com", "c8bcba63-82ac-402e-8ab0-99171a7a4dc2"),
    Pair("smoketester+af21acbd40@wire.com", "3255ed0a-6648-439c-91b9-1a0a109872ae"),
    Pair("smoketester+3efdcf74f3@wire.com", "284ea777-3d20-4aaf-b6bc-6587513f2ec9"),
    Pair("smoketester+24b886f680@wire.com", "13518b28-7a2b-4fe0-b102-a580f2247e7e"),
    Pair("smoketester+61fd980813@wire.com", "22e2316c-fa99-4e0a-a980-f7d344bb160e"),
    Pair("smoketester+eda11bde4e@wire.com", "0bda6e48-285a-4e6d-bf83-e2271c2444d7"),
    Pair("smoketester+d81e502923@wire.com", "fde1e703-23a1-4d84-9843-2f786b07607f"),
    Pair("smoketester+e4e25cd0ca@wire.com", "e2cfaa71-233b-4684-bc4b-cc27385ec23a"),
    Pair("smoketester+77563a45aa@wire.com", "9933258c-fa78-48d7-b8e0-ae1394bd8320"),
    Pair("smoketester+a1f190f32e@wire.com", "ddf61858-7b1e-4267-9570-f1521b127b52"),
    Pair("smoketester+37135872a1@wire.com", "0b4ded8b-592d-4e72-a50f-8982f171475c"),
    Pair("smoketester+40e6ef3d51@wire.com", "2cce899a-08c2-4764-ac80-5a437088c800"),
    Pair("smoketester+6a9cd77947@wire.com", "7b358125-3f49-4fda-9581-1ce6ad26eeec"),
    Pair("smoketester+1b6a33b21c@wire.com", "9b0c6cf8-a3c7-494e-8802-22d2c690cecf"),
    Pair("smoketester+65b26f48ad@wire.com", "49634f58-a75f-4c46-aa8b-187c40fe44c4"),
    Pair("smoketester+4fba796ddc@wire.com", "41805513-e2f9-4185-b4d0-7e9598f9b2f5"),
    Pair("smoketester+1c7e3f8b5c@wire.com", "a7d4ee45-4625-4963-8b95-213d29f7a53b"),
    Pair("smoketester+942e024261@wire.com", "68b169d6-362b-462c-9d1d-1f306c532596"),
    Pair("smoketester+cca5a61ead@wire.com", "f5d7c6ba-f8da-432e-9051-06ec4616407c"),
    Pair("smoketester+165a3d0662@wire.com", "7d7a43f7-c877-476e-a474-80d882c17abb"),
    Pair("smoketester+79c55b5635@wire.com", "7d92cdd8-ef92-4a36-8bcc-5060c4d7866a"),
    Pair("smoketester+27bbe2bd51@wire.com", "b4108e71-8948-4b66-a7f6-fa310570262e"),
    Pair("smoketester+b915f28d7c@wire.com", "87e3e52e-cd9f-43f4-9e2f-b5f686b8b172"),
    Pair("smoketester+81ba7e1566@wire.com", "79edc043-212b-4453-85ff-bbbdfc256297"),
    Pair("smoketester+c15bbc3388@wire.com", "c13b41f5-b980-4d9c-9dcb-2d9ed81dba94"),
    Pair("smoketester+b5eef2fd34@wire.com", "af38c1e1-4cd9-4687-b77d-6b159b144e87"),
    Pair("smoketester+17b9bdfab3@wire.com", "15a7fc23-6f5c-40fa-8921-c5114539ce9a"),
    Pair("smoketester+6799e7314e@wire.com", "ef26e71d-7d16-41c8-90cf-0379d4506387"),
    Pair("smoketester+f982bd1297@wire.com", "4766a7ee-db57-47da-99f1-ddd169cb770d"),
    Pair("smoketester+ab7a72bf46@wire.com", "bff60594-5618-41af-a46f-06fbfb6eda13"),
    Pair("smoketester+c1e0b81d20@wire.com", "faa47817-8052-4b7d-a0f7-d3bbbe77e48f"),
    Pair("smoketester+0efac66afb@wire.com", "f0550316-a614-45f9-83be-4d9175161327"),
    Pair("smoketester+5af790ca4d@wire.com", "5cbf6e4e-bc54-443b-a298-2430307de8fe"),
    Pair("smoketester+fcc1303294@wire.com", "21f28733-0885-41cd-9dbd-9b3958ea2707"),
    Pair("smoketester+d558eff4de@wire.com", "8fd3f1b4-e8f8-40a0-a052-65a8bc3702b3"),
    Pair("smoketester+6a3cb12662@wire.com", "47c886d1-cfd8-47fd-b96a-73ff4b659bfe"),
    Pair("smoketester+434f48b1ec@wire.com", "344e1b11-2025-484d-81f9-4594a7daf8d2"),
    Pair("smoketester+dd43708fe7@wire.com", "119e28e0-ff93-419d-a330-7fdbd621910e"),
    Pair("smoketester+f6d2048d79@wire.com", "c63489ba-55db-4d2b-b5c3-1932ab3afdd0"),
    Pair("smoketester+b8a0dde95c@wire.com", "5fc2ba25-77b0-4370-bf7b-e6fd2ba90833"),
    Pair("smoketester+7a6746ee72@wire.com", "b18381d0-8cdc-4ab0-b300-532e10950dd4"),
    Pair("smoketester+bb9ed029ba@wire.com", "1efd337f-5256-4ec2-8c61-7d219e83be93"),
    Pair("smoketester+0f30c07907@wire.com", "319aaace-80cb-432a-aec8-44d6d818f8a7"),
    Pair("smoketester+6d014728a6@wire.com", "1a61a9fd-f64b-488f-974d-bbed9e3a90c5"),
    Pair("smoketester+efc9eecefb@wire.com", "0cc409a9-7732-408d-881b-e9750fe79560"),
    Pair("smoketester+95cd7d467d@wire.com", "d5c1ad2f-60e9-4193-a2f3-d93ae45ecfd5"),
    Pair("smoketester+60147ebf3b@wire.com", "b7675f82-3fa2-4305-b9e1-e45c5d6136f6"),
    Pair("smoketester+d6df451f50@wire.com", "b432d0cf-56a0-47fc-8ea3-8a8320f4a9ea"),
    Pair("smoketester+670cb90aa0@wire.com", "a0614786-dcca-4c2a-9a49-ce4b0ce1c00b"),
    Pair("smoketester+c8d33f3cd9@wire.com", "4f08c8d9-23be-4505-905a-dad8fab3ae42"),
    Pair("smoketester+cdd771e831@wire.com", "bd0ab18d-c821-4b0c-a86c-41224b3cbd84"),
    Pair("smoketester+0897b8742f@wire.com", "86813bdf-d101-4da4-bff0-e893508e32ec"),
    Pair("smoketester+09933be26a@wire.com", "ec01f751-56b8-4800-b00f-107e1bed3634"),
    Pair("smoketester+5ccdd7082d@wire.com", "ab8ae99d-5c41-4806-8aef-2bba5ea0e77a"),
    Pair("smoketester+ebf4e99e1f@wire.com", "c121b75b-34da-4087-afbc-0dfab1c7227a"),
    Pair("smoketester+b8d738df9a@wire.com", "cd1e3314-96ff-4b06-a73b-b18a37965c62"),
    Pair("smoketester+b6b9700b94@wire.com", "0868e7cb-6025-473f-ac6e-020694c6f218"),
    Pair("smoketester+600439252e@wire.com", "c5541f81-afd8-48fb-a4c9-7b634bb78d3d"),
    Pair("smoketester+28e1acc9d6@wire.com", "ef7991fa-27c9-405b-845e-76dd24bcb212"),
    Pair("smoketester+0f0e77d3c4@wire.com", "a96262f7-122d-4942-8d9a-2cbc26fde204"),
    Pair("smoketester+2d49a445a6@wire.com", "79c5c43d-8445-4fca-b180-a82497968d92"),
    Pair("smoketester+c03a71c915@wire.com", "12fe370a-4f94-449a-9312-30ea72cbbf30"),
    Pair("smoketester+c038103971@wire.com", "c44176aa-8003-4936-bab7-2f857b2d2f53"),
    Pair("smoketester+a747b2e348@wire.com", "bdb1db31-238c-42ff-926c-b4817d470ee8"),
    Pair("smoketester+8f919b8e96@wire.com", "80f1c026-cbf7-47b4-b7e8-3a019395b5af"),
    Pair("smoketester+698202d05b@wire.com", "f5d982aa-9e8b-4015-8907-a786c90e473c"),
    Pair("smoketester+f8defd5a65@wire.com", "1533b9e3-0ca1-465a-b0e4-31e1aebc5b5a"),
    Pair("smoketester+9b9282b9a0@wire.com", "438f4447-a7e3-4cb3-a70a-da7311222be1"),
    Pair("smoketester+99a3232ee2@wire.com", "bdf2dc7a-7ba0-41a5-9405-36428daed787"),
    Pair("smoketester+39234ae39a@wire.com", "2844354d-2e96-4076-8737-fb8c619d3818"),
    Pair("smoketester+47584bbf8b@wire.com", "1e52f4b4-c93e-49d4-9740-1478be2e298a"),
    Pair("smoketester+d826fe0543@wire.com", "865d11ea-fb33-481f-af29-89793a4d721b"),
    Pair("smoketester+338c6a29df@wire.com", "c4a528a3-168d-41b3-ae94-2bd2954c243f"),
    Pair("smoketester+66446d951a@wire.com", "cb943914-bdd8-4cb7-9b63-92aae8cf6b20"),
    Pair("smoketester+706439f0b6@wire.com", "21e95089-0f39-43df-be5a-b986484f4014"),
    Pair("smoketester+34ec733d6f@wire.com", "3c401b8e-174e-40f4-9a72-c056a5d59453"),
    Pair("smoketester+939cc470da@wire.com", "da8afa2c-f048-4690-af40-4b2a36191918"),
    Pair("smoketester+8735ab2970@wire.com", "5b396e8c-7883-46c4-a009-02d6a0b4a822"),
    Pair("smoketester+4d4efdc5c9@wire.com", "a4487de8-e002-47f5-9610-881fc0505f4e"),
    Pair("smoketester+ef5a273868@wire.com", "7fe46210-0c15-4252-aeeb-fe3029f46da9"),
    Pair("smoketester+9f7b0e4793@wire.com", "3fb44fca-6470-4c04-8379-59f1286f25e5"),
    Pair("smoketester+526bcc6e93@wire.com", "156379ec-eb7e-40b9-940f-7932d9815797"),
    Pair("smoketester+b8802d5511@wire.com", "4f61e916-54a7-4ad1-bffc-16555f01014e"),
    Pair("smoketester+2c76c37422@wire.com", "4bd5bbba-1fba-4ce0-a9a7-4089226a124b"),
    Pair("smoketester+3801c39032@wire.com", "71814c21-1dff-4124-80c0-cf3a5ec8fc59"),
    Pair("smoketester+733e5240a3@wire.com", "caf0b210-e3f1-430d-9e87-61e40ba94149"),
    Pair("smoketester+9b6090ed77@wire.com", "ef671d01-5967-4e23-862e-fedfee6d77b2"),
    Pair("smoketester+a3886914ae@wire.com", "61159822-2015-4fe1-a5ab-86ba3e664332"),
    Pair("smoketester+a2e2308abf@wire.com", "7a3a2850-a77d-49a7-979a-c1c842477161"),
    Pair("smoketester+e8a59dd718@wire.com", "f0ee0c7d-1700-4e15-8ae6-945bb25660f9"),
    Pair("smoketester+bc5bbe3a14@wire.com", "d43ff184-2917-41ca-9d24-d00a27648a3d"),
    Pair("smoketester+021c2dd5ea@wire.com", "73fa8020-52fe-42dd-8d77-9bc8a9e61064"),
    Pair("smoketester+840cb12f12@wire.com", "01e3373f-7cb8-4d49-8653-2f11abcc5a0a"),
    Pair("smoketester+359a1708ae@wire.com", "010042ba-1239-4876-9979-4b5c975bc8f9"),
    Pair("smoketester+00eb48d256@wire.com", "b578ed94-1dac-4ea1-9e34-73fb53e559c6"),
    Pair("smoketester+5345d09b75@wire.com", "561bc454-92ca-478c-b9a7-6fd6551aef44"),
    Pair("smoketester+9b1ea31ed2@wire.com", "6bbf918d-11d8-49c5-ac99-6229453d8253"),
    Pair("smoketester+de4c3be125@wire.com", "022f1099-847c-415b-ba9a-94270eb5d4f9"),
    Pair("smoketester+45453b532f@wire.com", "36e846eb-cb5a-4747-9d83-78a72dc5432a"),
    Pair("smoketester+56c0514c4a@wire.com", "342e8c2c-e6ee-4f49-8738-7df29d2469ee"),
    Pair("smoketester+d405714f02@wire.com", "f8fc338f-98fc-400b-97f0-cd9ca897e853"),
    Pair("smoketester+712b0739a4@wire.com", "908ed67b-22cb-44ab-822b-03e3849b681d"),
    Pair("smoketester+6bd1b8962e@wire.com", "8e0d6271-71ec-4c72-b592-90fcd568fe2b"),
    Pair("smoketester+8daf2a8d9d@wire.com", "9cd1749f-4df7-4753-bb83-80f8776be61c"),
    Pair("smoketester+9cbe259562@wire.com", "7e9754a4-f88f-4d1d-b084-967b8b1d137b"),
    Pair("smoketester+f63a48f9f1@wire.com", "8e8cd9bd-f860-42e5-9077-8d9ba0c2b1be"),
    Pair("smoketester+1c8911113b@wire.com", "92a1f854-e155-49e8-8ed5-b4d2ea95c07a"),
    Pair("smoketester+759f2872d5@wire.com", "ed6fcd4b-6cc1-4251-8f62-c9dfcdb78190"),
    Pair("smoketester+fa62f71b8a@wire.com", "92e4f7e2-25d1-4295-856f-2c03e415ede8"),
    Pair("smoketester+a221196866@wire.com", "6839c483-e93e-4e9f-b0a7-abdd8af42bcb"),
    Pair("smoketester+ed83586444@wire.com", "ca48dbb9-68f9-43bb-bc74-2ae386a704f4"),
    Pair("smoketester+47357a9ff4@wire.com", "1ced1b20-ff09-414f-b9bf-785d7b2ccb12"),
    Pair("smoketester+18d1884423@wire.com", "7b994cd0-d4fb-4273-845c-349399c4a3d4"),
    Pair("smoketester+d56f7b53db@wire.com", "d86689f6-8525-4554-a759-6cb6fc35b4df"),
    Pair("smoketester+eedf46261c@wire.com", "d4538dcb-35ec-40e0-aa2b-159ce22e5f05"),
    Pair("smoketester+13d9161cdd@wire.com", "535a7b9e-ef93-45b6-805d-a76478f3a2a5"),
    Pair("smoketester+4e85c7140d@wire.com", "5df3d34c-17c2-4a0b-8b53-31a1acd3cfc1"),
    Pair("smoketester+d3f750330e@wire.com", "a907f083-0d58-4ee1-9795-680bb4e55dce"),
    Pair("smoketester+a91f3bedac@wire.com", "819c1439-8246-44ed-9eee-3db89e83df9f"),
    Pair("smoketester+8667c5387b@wire.com", "36504df4-1f3b-4682-b5ac-93e5b54b24fe"),
    Pair("smoketester+316bd9426e@wire.com", "945bbe0d-4358-41aa-aab6-af1cf4c4d534"),
    Pair("smoketester+60f33872ec@wire.com", "d82abcad-ed44-4446-bde7-7e21a671aab1"),
    Pair("smoketester+8a8c243a8c@wire.com", "ee554f5a-120e-41f6-a027-0dfd8c105ed8"),
    Pair("smoketester+aeb4a0cb33@wire.com", "d5300073-45a7-4470-9359-af1972a28e14"),
    Pair("smoketester+2df1570633@wire.com", "4aa033cd-b9f5-48b4-b5cb-49ebe49f44d2"),
    Pair("smoketester+3a57cfde9b@wire.com", "9106c71c-bf0e-48d9-b30f-2fe57f69987c"),
    Pair("smoketester+d2ac5bde2f@wire.com", "24537aa5-c59b-48b3-996a-b4608199dbc8"),
    Pair("smoketester+19f1ddb2e1@wire.com", "870f5d09-40fd-4e3a-9f1e-c7065d76f220"),
    Pair("smoketester+d6fda293a4@wire.com", "a68b7f02-c17d-4c5c-8101-6ccaccb4a367"),
    Pair("smoketester+06d00a9beb@wire.com", "7e3d33d3-5633-42ba-a0ae-7aba82021549"),
    Pair("smoketester+c132b4e41d@wire.com", "0dae817a-1ef8-474b-8db1-624d7f8f42e3"),
    Pair("smoketester+9814a44d72@wire.com", "b1d8ed26-b6d9-4e0e-bfb7-91ca937eb2d1"),
    Pair("smoketester+8838f02b86@wire.com", "4dbdf883-fceb-4d4a-a439-2a614b6ad5f3"),
    Pair("smoketester+b03620bee1@wire.com", "1e99cee0-c939-4336-93fe-baffe9ed3b36"),
    Pair("smoketester+d54179f45c@wire.com", "80863caa-9408-44ba-95e5-d456246a8dd8"),
    Pair("smoketester+4fac5e3054@wire.com", "5468d10f-ddd8-4dbf-aa43-f2294d5228ca"),
    Pair("smoketester+38fec64b1b@wire.com", "76a9c9d1-5b46-4418-b979-446255023a5d"),
    Pair("smoketester+efd076fd49@wire.com", "0af550a2-a70d-4de6-82e7-12c8e4d3b456"),
    Pair("smoketester+53ff7123ce@wire.com", "0134cf18-9c8a-46ec-a8e6-736b63c34fbd"),
    Pair("smoketester+326fc6fab6@wire.com", "aeba8316-db59-4512-9bab-82c0dc6e7b0e"),
    Pair("smoketester+c00bbe1d09@wire.com", "2c713e6b-607f-4de8-9f15-f6c076b8a2bb"),
    Pair("smoketester+b61984aeb7@wire.com", "29f688db-a496-4499-9ec7-9228d911e95c"),
    Pair("smoketester+c9acb496c5@wire.com", "6ac2ad24-1931-41fe-b905-b905497a6500"),
    Pair("smoketester+37bd4edd60@wire.com", "16e18145-063c-43ca-84b2-1c84a3dcc9aa"),
    Pair("smoketester+4e1dca8854@wire.com", "1e80ade3-808e-411d-9f69-4095490e12b9"),
    Pair("smoketester+f9bcf07f1c@wire.com", "473753c9-f167-481f-abef-e3850497fa96"),
    Pair("smoketester+f407072e69@wire.com", "a5f1d6d5-c19f-4d2b-bc15-16986e957dc2"),
    Pair("smoketester+228e4079e4@wire.com", "9deed74c-eeaa-4aab-844e-6ddab847c1d6"),
    Pair("smoketester+fa953c8a0d@wire.com", "bd5d7cab-9737-43b1-8afc-f665b24f3964"),
    Pair("smoketester+188bcd5780@wire.com", "2e222f1d-5e5a-42fc-b9f8-f1dc265ca614"),
    Pair("smoketester+7977cbc887@wire.com", "d4560ce4-be1b-42b4-ab1b-baa6343e6417"),
    Pair("smoketester+3664d92d6a@wire.com", "ef2695b0-ab65-4cb9-96a0-9a4460854416"),
    Pair("smoketester+a2af9abcd2@wire.com", "064622ac-4446-409b-ab48-8fb3ad0c175b"),
    Pair("smoketester+a93a8a950f@wire.com", "56c25fb8-1cd2-4f2a-aec4-d68fe3af5c1f"),
    Pair("smoketester+362c58cd6c@wire.com", "fa41ffe6-00ba-4bf0-a0fe-2b23c65d5414"),
    Pair("smoketester+650b4aab1d@wire.com", "49979b16-0089-45ba-b7d0-6cbc3e115e2e"),
    Pair("smoketester+f3d3f92324@wire.com", "8e4904aa-086d-4cd3-915c-18438d41725e"),
    Pair("smoketester+1ca3f93bad@wire.com", "7c40978e-a748-4410-820f-4fbeddb28bda"),
    Pair("smoketester+1b42f19b9a@wire.com", "1cdd04dc-f81a-4a0a-9abc-193f3510ce6c"),
    Pair("smoketester+9cd9a318ea@wire.com", "8d42b82c-a33b-45f5-b244-ae4948615bd5"),
    Pair("smoketester+aeb252b103@wire.com", "a810fe49-28f0-4af3-a99b-3c8aecea661d")
)

val users = team_200.map { UserData(it.first, password, UserId(it.second, domain)) }
