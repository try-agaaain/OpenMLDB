# Hive

## 简介

[Apache Hive](https://hive.apache.org/) 是一款常用的数据仓库工具。OpenMLDB 提供对于 Hive 作为数据仓库的导入和导出操作。一般情况下，Hive 作为离线数据仓场景较为普遍，但是也可以作为在线引擎冷启动时候的在线数据导入的数据源。

## 配置

### 安装

[OpenMLDB Spark 发行版](../../tutorial/openmldbspark_distribution.md) v0.6.7 及以上版本均已经包含 Hive 依赖。如果使用其他 Spark 发行版，使用以下步骤进行安装。

1. 在 Spark 中执行如下命令编译 Hive 依赖

```bash
./build/mvn -Pyarn -Phive -Phive-thriftserver -DskipTests clean package
```


2. 执行成功以后，依赖包在目录`assembly/target/scala-xx/jars`
2. 将所有依赖包加入 Spark 的 class path 中。

### 配置

目前 OpenMLDB 只支持使用 metastore 服务来连接Hive。你需要进行以下相关配置以正确访问 Hive 数据源。

- spark.conf：你可以在 spark conf 中配置 `spark.hadoop.hive.metastore.uris`。有两种方式：

  - taskmanager.properties: 在配置项 `spark.default.conf` 中加入`spark.hadoop.hive.metastore.uris=thrift://...` ，随后重启taskmanager。
  - CLI: 在 ini conf 中加入此配置项，并使用`--spark_conf`启动CLI，参考[客户端Spark配置文件](../../reference/client_config/client_spark_config.md)。
  
- hive-site.xml：你可以配置 `hive-site.xml` 中的 `hive.metastore.uris`，并将配置文件放入 Spark home的`conf/`。`hive-site.xml` 样例：

  ```xml
  <configuration>
  	<property>
  		<name>hive.metastore.uris</name>
  		<!--Make sure that <value> points to the Hive Metastore URI in your cluster -->
  		<value>thrift://localhost:9083</value>
  		<description>URI for client to contact metastore server</description>
  	</property>
  </configuration>
  ```

### 调试信息

确认任务是否连接到正确的 Hive 集群，可以通过任务日志来查看。

- `INFO HiveConf:`提示读取到的是哪个 Hive 配置文件。如果需要配置加载的细节，可以打开 Spark 的日志。
- 连接 Hive metastore 应该有` INFO metastore: Trying to connect to metastore with URI`的日志提示。连接成功会有`INFO metastore: Connected to metastore.`日志。

## 数据格式

目前仅支持以下几种 Hive 的数据格式：

| OpenMLDB 数据格式 | Hive 数据格式 |
| ----------------- | ------------- |
| BOOL              | BOOLEAN       |
| SMALLINT          | SMALLINT      |
| INT               | INT           |
| BIGINT            | BIGINT        |
| FLOAT             | FLOAT         |
| DOUBLE            | DOUBLE        |
| DATE              | DATE          |
| TIMESTAMP         | TIMESTAMP     |

## 导入 Hive 数据到 OpenMLDB

对于 Hive 数据源的导入是通过 API [`LOAD DATA INFILE`](../../openmldb_sql/dml/LOAD_DATA_STATEMENT.md) 进行支持，通过使用特定的 URI 接口 `hive://[db].table` 的格式进行导入 Hive 内的数据。注意：

- 离线和在线引擎均可以导入 Hive 数据源
- 参数 `deep_copy` 仅支持取值 `true` 
- `OPTIONS` 参数仅有 `deep_copy` 和 `mode` 有效

举例：

```sql
LOAD DATA INFILE 'hive://db1.t1' INTO TABLE t1 OPTIONS(deep_copy=true);
```

## 导出 OpenMLDB 数据到 Hive

对于 Hive 数据源的导出是通过 API [`SELECT INTO`](../../openmldb_sql/dql/SELECT_INTO_STATEMENT.md) 进行支持，通过使用特定的 URI 接口 `hive://[db].table` 的格式进行导出到 Hive 数仓。注意：

- 如果不指定数据库名字，则会使用默认数据库名字 `default_db`
- 如果指定数据库名字，则该数据库必须已经存在，目前不支持对于不存在的数据库进行自动创建
- 如果不指定表格名字，则会在 Hive 内自动创建对应名字的表格
- `OPTIONS` 参数均不生效

举例：

```sql
SELECT col1, col2, col3 FROM t1 INTO OUTFILE 'hive://db1.t1';
```

