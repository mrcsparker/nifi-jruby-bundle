require 'recursive_open_struct'
require 'json'

flow_file = session.get()
if flow_file.nil?
  return
end

h = { :somearr => [ { name: 'a'}, { name: 'b' } ] }
ros = RecursiveOpenStruct.new(h, recurse_over_arrays: true )
json =  JSON.generate(ros.to_h)

flow_file = session.putAttribute(flow_file, "from-content", json)
session.transfer(flow_file, REL_SUCCESS)