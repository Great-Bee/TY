# ty-server
TY Server

---------------------------------------
### 默认启动方式
<pre><code>
java -jar ty-server-1.0-SNAPSHOT.jar
</code></pre>
<p>**默认启动方式会加载jar里面自带的config.properties文件，里面配置了系统用到的常量，比如：数据库链接信息。</p>

### 自定义启动方式
<pre><code>
java -jar ty-server-1.0-SNAPSHOT.jar --configPath=/apps/config/config.properties
</code></pre>
<p>**configPath可以是服务器上任意路径下的配置文件。</p>
