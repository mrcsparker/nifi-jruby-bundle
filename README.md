# nifi-jruby-bundle

NIFI interface that uses the [JRuby Scripting Container](http://jruby.org/apidocs/org/jruby/embed/ScriptingContainer.html) rather than the [JSR223 API](https://en.wikipedia.org/wiki/Scripting_for_the_Java_Platform).

## Why use over the default NIFI Scripting Processor?

This code is based on the default NIFI scripting processor, and that processor is excellent.  This processor has a few extra features:

* Use local gems installed on the system
* Point to a different JRuby instance than the one with NIFI
* Get line numbers along with errors if the script fails to run
* Uses the latest version of JRuby

## Install

* Download [Apache NIFI](https://nifi.apache.org) 1.0.
* From inside this project run:

```
> mvn package
```

* Copy `./nifi-jruby-nar/target/nifi-jruby-nar-0.0.1.nar` to $NIFI_INSTALL/lib/
* Run NIFI

## Sample code

```ruby
java_import org.apache.commons.io.IOUtils
java_import java.nio.charset.StandardCharsets

java_import org.apache.nifi.processor.io.StreamCallback
java_import org.apache.nifi.processors.jruby.JRubyProcessor

require 'json'

class Transform

  include StreamCallback

  def initialize
    @buffer = ''
  end

  def process(in_stream, out_stream)
    buffer = IOUtils.toString(in_stream)

    o = parse(buffer)
    out_stream.write(o.to_java_bytes)
  end

  private
  def parse(buffer)
    buffer.split(';').map { |x| y = x.split('='); { y[0] => y[1] } }.to_json
  end
end

flow_file = session.get()
if flow_file.nil?
  return
end

input = session.putAttribute(flow_file, "read", flow_file.size.to_s)


begin
  output = session.write(input, Transform.new)
  session.transfer(output, JRubyProcessor::REL_SUCCESS)
rescue Exception => e
  puts e.message
  print e.backtrace.join("\n")
  input = session.putAttribute(input, "error", e.to_s)
  session.transfer(input, JRubyProcessor::REL_FAILURE)
end
```

## LICENSE

[Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)
