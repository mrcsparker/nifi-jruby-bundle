require 'calc/adder'

flowFile = session.get()
if flowFile.nil?
  return
end

adder = Adder.new
res = adder.add_string(1, 2)

flowFile = session.putAttribute(flowFile, "from-content", "Hello world: #{res}")
session.transfer(flowFile, REL_SUCCESS)