flowFile = session.get()
if flowFile.nil?
  return
end

flowFile = session.putAttribute(flowFile, "from-content", "Hello world")
session.transfer(flowFile, REL_SUCCESS)