java_import org.apache.commons.io.IOUtils
java_import java.nio.charset.StandardCharsets

require 'json'

class Transform
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
  session.transfer(output, REL_SUCCESS)
rescue Exception => e
  puts e.message
  print e.backtrace.join("\n")
  input = session.putAttribute(input, "error", e.to_s)
  session.transfer(input, REL_FAILURE)
end
