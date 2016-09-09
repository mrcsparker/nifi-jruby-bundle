flowFile = session.get()
if flowFile.nil?
  return
end

x

flowFile = session.putAttribute(flowFile, "from-content", "Hello world")
session.transfer(flowFile, REL_SUCCESS)