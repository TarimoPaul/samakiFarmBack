# Backend — Spring Boot (Java) + GraphQL + REST

## Muundo wa API
- **REST** (`/api/**`) — Auth, User Management, Roles & Permissions, Farms
- **GraphQL** (`/graphql`) — kila kitu kingine: Production Units, Cycles (na baadaye Feed, Water Quality, Daily Tasks, Finance)
- RBAC moja (`PermissionChecker`) inatumika NA REST NA GraphQL — chanzo kimoja cha ukweli, si logic mbili tofauti

Code imepangwa kwa **module** (si kwa tabaka): kila dhana ina folder yake yenye `entity/`, `dto/`, `repository/`, `services/`, na `controller/` au `graphql/`. Module mpya ni kuongeza folder tu.

## Mfumo wa umiliki na akaunti

Mfumo ni wa **kampuni moja** yenye mashamba yanayoweza kuwa zaidi ya moja.

- **`users`** = mtu (utambulisho + hali ya akaunti). **`farm_users`** = uanachama (`user_id` + `farm_id` + `role_id`). Mtu anaweza kuwa kwenye mashamba mengi, na role yake ni ya kila shamba — si ya mtu.
- **`users.status`** ni mzunguko wa maisha: `PENDING_APPROVAL` → `ACTIVE` → `DISABLED`. Ni tofauti kabisa na `is_deleted` (ambayo ni kufuta rekodi pekee).
- **Kujisajili** (`POST /api/auth/register`) kunaunda mtu `PENDING_APPROVAL` **pekee** — hakuna shamba, hakuna role, hakuna token.
- **Kuidhinisha** (`POST /api/users/{id}/approve`) kunahitaji ruhusa ya `approve_users` na kunabadilisha hali kuwa `ACTIVE`. **Hakutoi role.**
- **Kupewa uanachama** (`POST /api/users/{id}/memberships`) ni hatua tofauti kabisa (`manage_users`). Mtu `ACTIVE` asiye na uanachama anaingia na kuona ukurasa mtupu — hii ni hali halali.

### Mkataba wa login

Password inathibitishwa **KWANZA**, kisha hali ya akaunti inaangaliwa. Mpangilio huu ndio unaozuia mtu kugundua ni namba zipi zilizosajiliwa.

| Hali | HTTP | `errorCode` |
|---|---|---|
| Password batili / mtu hayupo / amefutwa | `401` | `INVALID_CREDENTIALS` |
| Password sahihi + `PENDING_APPROVAL` | `403` | `PENDING_APPROVAL` |
| Password sahihi + `DISABLED` | `403` | `ACCOUNT_DISABLED` |
| Password sahihi + `ACTIVE` | `200` | — (token + `mustChangePassword`) |

Frontend inatawi kwa **`errorCode`**, si kwa ujumbe wa Kiswahili.

## RBAC

- **JWT haibebi ruhusa.** Inabeba `userId` + `isRoot` (+ `farmId`/`roleId`/`roleName` kwa muktadha wa UI). Kila request, `JwtAuthFilter` inasoma hali ya akaunti na ruhusa **fresh kutoka DB** (cache: dakika 15 kwa mtumiaji, dakika 5 kwa ROOT). Ukibadilisha role, kuzuia, au kufuta mtu — inaanza kufanya kazi papo hapo bila kusubiri token iishe muda.
- **ROOT ni flag (`users.is_root`), si jina la role.** Hana uanachama wowote. Anatengenezwa na `RbacSeedService` kutoka environment variables pekee, na analazimika kubadilisha password mara ya kwanza.
- **Idhini inadhibitiwa na RUHUSA (`approve_users`), si jina la role** — role yoyote iliyopewa ruhusa hiyo inaweza kuidhinisha.
- Permissions zinapakiwa kutoka `seed/permissions.csv` (idempotent). Role↔permission zinapakiwa kutoka `seed/role_permissions.csv` **mara moja tu kwa role isiyo na ruhusa yoyote** — role iliyobadilishwa na admin haiguswi tena kwenye restart. Kwa hiyo **ruhusa mpya kwa role zilizopo lazima ziongezwe kwa migration**, si kwa CSV pekee (angalia `V7__auth_permissions.sql`).

## Kuendesha

Mahitaji: JDK 17+, Maven, PostgreSQL.

**Environment variables za LAZIMA** — app haitaanza bila hizi (angalia `.env.example`):

| Variable | Maana |
|---|---|
| `DB_PASSWORD` | password ya PostgreSQL |
| `JWT_SECRET` | siri ndefu ya nasibu (angalau herufi 32) |

Za hiari lakini muhimu: `ROOT_PHONE`, `ROOT_PASSWORD`, `ROOT_EMAIL` — zisipowekwa, **ROOT hatengenezwi** (app inaanza, lakini kwa onyo kwenye logs).

```powershell
$env:DB_PASSWORD = "..."
$env:JWT_SECRET  = "..."
$env:ROOT_PHONE  = "0000000000"
$env:ROOT_PASSWORD = "..."
mvn spring-boot:run
```

Flyway inaendesha migrations kiotomatiki. GraphiQL: `http://localhost:8082/graphiql`.

## Documents za schema

`Data_Dictionary_Majedwali.md` na `ERD_Muundo_wa_Database.mermaid` **hazihaririwi kwa mkono** — zinazalishwa kutoka database halisi:

```powershell
$env:PGPASSWORD = "..."; ./tools/generate-docs.ps1
```

Ziendeshe baada ya kila migration. Hii ndiyo inayozuia drift kama ile ya awali ("Data Dictionary inasema 17, ERD inasema 20").

## MUHIMU — Lombok + JDK ya kisasa kwenye Maven

Lombok 1.18.34 (iliyosimamiwa na spring-boot-dependencies 3.3.2) ilikuwa ikishindwa **kimya kimya** kutengeneza getters/setters wakati wa `mvn compile` kwenye JDK 23 — makosa ya "cannot find symbol" kila mahali bila kosa lolote la Lombok lenyewe. Chanzo: "auto-discovery" ya annotation processor kutoka kwenye `-classpath` ndefu kwenye njia yenye nafasi (`D:\KAMPUNI PROJECT\...`). Suluhisho (tayari kwenye `pom.xml`): `<lombok.version>1.18.42</lombok.version>` + `<annotationProcessorPaths>` ya wazi.

## Bado Haijaandikwa

- Entities/resolvers za: FeedPurchase, FeedingLog, FeedStockMovement, WaterQualityLog, TaskCompletion, Reminder, Cost, Customer, Sale, Asset
- API za `species` na kubadilisha jina/mahali pa shamba
- Kubadilisha shamba (farm switching) — muundo unaruhusu uanachama mwingi, lakini token inabeba shamba MOJA (angalia `// TODO: farm switching`)
- Reminders scheduler (Spring `@Scheduled`)
- Angular frontend (haijaguswa)

Angalia `GAP_ANALYSIS.md` kwa uchambuzi kamili wa kilichopo dhidi ya kinachotakiwa.

## Kabla ya production

- Zima GraphiQL (`spring.graphql.graphiql.enabled: false`)
- Badilisha `LoggingSmsSender` (stub inayoandika kwenye logs) na provider halisi — **kwa sasa OTP HAITUMWI kweli**
- Ongeza `spring-boot-starter-actuator` au ondoa ruhusa ya `/actuator/health` kwenye `SecurityConfig`
- Rate limiting ya sasa iko kwenye kumbukumbu ya instance moja — kwa instance nyingi inahitajika Redis au sawa
- Hakikisha load balancer inaandika upya `X-Forwarded-For` (angalia `ClientIp`)

## AWS Deployment (baadaye)

Package kama Docker image (JAR ya Spring Boot + JRE 17 base image), deploy kwenye ECS Fargate — RDS PostgreSQL kama database.
